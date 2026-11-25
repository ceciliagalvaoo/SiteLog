package hr.algebra.sitelog.api.reopen;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import hr.algebra.sitelog.api.docs.ApiDocs;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.dailylog.domain.LogReopenRequest;
import hr.algebra.sitelog.dailylog.service.DailyLogHistoryService;
import hr.algebra.sitelog.dailylog.service.DailyLogService;
import hr.algebra.sitelog.dailylog.service.LogReopenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * AR1 part 2 over REST: the audited reopen of a signed-off daily log and its revision history.
 * {@code request} (SITE_MANAGER of the log's site) -> {@code approve} / {@code reject}
 * (SAFETY_SUPERVISOR). Rules are in {@link LogReopenService}; the revisions come from Envers
 * through {@link DailyLogHistoryService}.
 *
 * <p>Approve and reject are {@code POST} sub-resources: a decision with side effects (the log
 * becomes REOPENED with revision + 1, who and when are recorded), not a field to PUT.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reopen", description = """
		AR1: a signed-off daily log is locked. Its SITE_MANAGER requests a reopen with a reason, a \
		SAFETY_SUPERVISOR approves (log REOPENED, revision + 1) or rejects with a reason. Every change \
		stays in the revision history (Envers).""")
public class ReopenApiController {

	private final LogReopenService reopenService;

	private final DailyLogService dailyLogService;

	private final DailyLogHistoryService historyService;

	public ReopenApiController(LogReopenService reopenService, DailyLogService dailyLogService,
			DailyLogHistoryService historyService) {
		this.reopenService = reopenService;
		this.dailyLogService = dailyLogService;
		this.historyService = historyService;
	}

	@GetMapping("/daily-logs/{id}/reopen-requests")
	@Operation(summary = "List the reopen requests of a daily log", description = "Every request of the log, newest first, including the pending one.")
	@ApiResponse(responseCode = "200", description = "The reopen requests")
	@ApiResponse(responseCode = "404", description = "No daily log with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	public List<ReopenRequestResponse> list(@PathVariable Long id) {
		return reopenService.panel(id).history().stream().map(request -> ReopenRequestResponse.of(id, request)).toList();
	}

	@GetMapping("/daily-logs/{id}/reopen-requests/{requestId}")
	@Operation(summary = "Get a reopen request")
	@ApiResponse(responseCode = "200", description = "The reopen request")
	@ApiResponse(responseCode = "404", description = "No such request on this daily log",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	public ReopenRequestResponse get(@PathVariable Long id, @PathVariable Long requestId) {
		return response(id, requestId);
	}

	@PostMapping("/daily-logs/{id}/reopen-requests")
	@Operation(summary = "Request a reopen", description = """
			Only the SITE_MANAGER of the log's site, only for a SIGNED_OFF log, at most one pending \
			request per log, reason mandatory. Every safety supervisor is notified.""")
	@ApiResponse(responseCode = "201", description = "Created (status PENDING); Location points to the new request",
			headers = @Header(name = "Location", description = "URL of the new reopen request"))
	@ApiResponse(responseCode = "400", description = "Missing version or reason",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "403", description = "Not the site manager of the log's site",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No daily log with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Stale version, log not signed off, or a request already pending",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = {
					@ExampleObject(name = "already pending", value = ApiDocs.CONFLICT_REOPEN_PENDING),
					@ExampleObject(name = "stale version", value = ApiDocs.CONFLICT_VERSION) }))
	public ResponseEntity<ReopenRequestResponse> request(@PathVariable Long id,
			@Validated @RequestBody ReopenRequestCreateRequest request) {
		Long requestId = reopenService.request(id, request.version(), request.reason());
		URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
			.path("/api/v1/daily-logs/{id}/reopen-requests/{requestId}")
			.buildAndExpand(id, requestId)
			.toUri();
		return ResponseEntity.created(location).body(response(id, requestId));
	}

	@PostMapping("/daily-logs/{id}/reopen-requests/{requestId}/approve")
	@Operation(summary = "Approve a reopen request", description = """
			SAFETY_SUPERVISOR only. The log becomes REOPENED with revision + 1 and can be corrected and \
			signed off again. The body (an optional note) may be omitted.""")
	@ApiResponse(responseCode = "200", description = "The approved request")
	@ApiResponse(responseCode = "400", description = "Note too long",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "403", description = "Not a safety supervisor",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No such request on this daily log",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Already decided, log no longer signed off, or a concurrent change of the log",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.CONFLICT_REOPEN_DECIDED)))
	public ReopenRequestResponse approve(@PathVariable Long id, @PathVariable Long requestId,
			@Validated @RequestBody(required = false) ReopenApproveRequest request) {
		reopenService.approve(id, requestId, request == null ? null : request.note());
		return response(id, requestId);
	}

	@PostMapping("/daily-logs/{id}/reopen-requests/{requestId}/reject")
	@Operation(summary = "Reject a reopen request", description = "SAFETY_SUPERVISOR only, reason mandatory; the log stays SIGNED_OFF.")
	@ApiResponse(responseCode = "200", description = "The rejected request")
	@ApiResponse(responseCode = "400", description = "Missing reason",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "403", description = "Not a safety supervisor",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No such request on this daily log",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Already decided",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.CONFLICT_REOPEN_DECIDED)))
	public ReopenRequestResponse reject(@PathVariable Long id, @PathVariable Long requestId,
			@Validated @RequestBody ReopenRejectRequest request) {
		reopenService.reject(id, requestId, request.reason());
		return response(id, requestId);
	}

	@GetMapping("/reopen-requests")
	@Operation(summary = "List the pending reopen requests", description = """
			The safety supervisors' work queue: PENDING requests on all sites, oldest first. Bounded (at \
			most one pending request per log), not paged.""")
	@ApiResponse(responseCode = "200", description = "The pending requests")
	@ApiResponse(responseCode = "403", description = "Not a safety supervisor",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	public List<PendingReopenResponse> pending() {
		return reopenService.pendingRequests().stream().map(PendingReopenResponse::of).toList();
	}

	@GetMapping("/daily-logs/{id}/revisions")
	@Operation(summary = "Revision history of a daily log", description = """
			Envers revisions of the log, its work entries and crew rows, newest first: who, when, and the \
			value before and after of every changed field.""")
	@ApiResponse(responseCode = "200", description = "The revisions")
	@ApiResponse(responseCode = "404", description = "No daily log with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	public List<RevisionResponse> revisions(@PathVariable Long id) {
		// 404 for an unknown log instead of an empty history
		dailyLogService.getHeader(id);
		return historyService.revisions(id).stream().map(RevisionResponse::of).toList();
	}

	private ReopenRequestResponse response(Long dailyLogId, Long requestId) {
		return reopenService.history(dailyLogId).stream()
			.filter(request -> request.id().equals(requestId))
			.findFirst()
			.map(request -> ReopenRequestResponse.of(dailyLogId, request))
			.orElseThrow(() -> new NotFoundException(LogReopenRequest.class, requestId));
	}

}
