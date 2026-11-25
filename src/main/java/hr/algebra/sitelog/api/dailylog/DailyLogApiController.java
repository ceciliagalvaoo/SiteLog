package hr.algebra.sitelog.api.dailylog;

import java.net.URI;
import java.util.Set;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import hr.algebra.sitelog.api.common.ApiPaging;
import hr.algebra.sitelog.api.common.PageResponse;
import hr.algebra.sitelog.api.docs.ApiDocs;
import hr.algebra.sitelog.dailylog.service.DailyLogService;
import hr.algebra.sitelog.site.service.SiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Daily logs: the collection belongs to a site ({@code /api/v1/sites/{siteId}/daily-logs}), a
 * single log has its own URL ({@code /api/v1/daily-logs/{id}}) because its id is unique on its
 * own. All rules are in {@link DailyLogService}: only the SITE_MANAGER of the log's site
 * writes (403), AR1 lock after sign-off (409), version check (409), date rules (422).
 *
 * <p>Sign-off is {@code POST .../sign-off}: a state transition with side effects (who and
 * when, lock), not a field the client may PUT.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Daily logs", description = """
		Daily logs of a site with their work entries and crew rows. Reading: any signed-in user; \
		writing and sign-off: the SITE_MANAGER of the log's site. A signed-off log is locked (409).""")
public class DailyLogApiController {

	static final Set<String> SORTABLE = Set.of("logDate", "status");

	private final DailyLogService dailyLogService;

	private final SiteService siteService;

	public DailyLogApiController(DailyLogService dailyLogService, SiteService siteService) {
		this.dailyLogService = dailyLogService;
		this.siteService = siteService;
	}

	@GetMapping("/sites/{siteId}/daily-logs")
	@Operation(summary = "List the daily logs of a site", description = """
			Paged and sorted (`?page=0&size=20&sort=logDate,desc`; size at most 100; sortable: logDate, \
			status; default logDate descending). Each row carries its work entry count, crew count and \
			crew hours.""")
	@ApiResponse(responseCode = "200", description = "One page of daily logs")
	@ApiResponse(responseCode = "400", description = "Unknown sort property",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "404", description = "No site with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	public PageResponse<DailyLogSummaryResponse> list(@PathVariable Long siteId,
			@ParameterObject @PageableDefault(size = 20, sort = "logDate", direction = Sort.Direction.DESC) Pageable pageable) {
		// 404 for an unknown site instead of an empty page
		siteService.getSummary(siteId);
		Pageable page = ApiPaging.restrict(pageable, SORTABLE, Sort.by(Sort.Direction.DESC, "logDate"));
		return PageResponse.of(dailyLogService.listForSite(siteId, page), DailyLogSummaryResponse::of);
	}

	@PostMapping("/sites/{siteId}/daily-logs")
	@Operation(summary = "Create a daily log", description = """
			The log with at least one work entry, each with its crew rows, in one request. Only the \
			SITE_MANAGER of this site. The date cannot be in the future and a site has one log per date.""")
	@ApiResponse(responseCode = "201", description = "Created (status DRAFT); Location points to the new log",
			headers = @Header(name = "Location", description = "URL of the new daily log"))
	@ApiResponse(responseCode = "400", description = "Invalid fields",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "403", description = "Not the site manager of this site",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No site with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "422", description = "Duplicate or future date, inactive or repeated crew",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.UNPROCESSABLE_LOG)))
	public ResponseEntity<DailyLogResponse> create(@PathVariable Long siteId,
			@Validated @RequestBody DailyLogRequest request) {
		Long id = dailyLogService.create(siteId, request.toCommand());
		URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
			.path("/api/v1/daily-logs/{id}")
			.buildAndExpand(id)
			.toUri();
		return ResponseEntity.created(location).body(DailyLogResponse.of(dailyLogService.getDetail(id)));
	}

	@GetMapping("/daily-logs/{id}")
	@Operation(summary = "Get a daily log", description = "The whole aggregate (work entries and crew rows) and its current version.")
	@ApiResponse(responseCode = "200", description = "The daily log")
	@ApiResponse(responseCode = "404", description = "No daily log with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	public DailyLogResponse get(@PathVariable Long id) {
		return DailyLogResponse.of(dailyLogService.getDetail(id));
	}

	@PutMapping("/daily-logs/{id}")
	@Operation(summary = "Replace a daily log", description = """
			Replaces the log and synchronises its work entries and crew rows (with id: changed, without \
			id: added, left out: removed). `version` must be the current one.""")
	@ApiResponse(responseCode = "200", description = "The updated daily log (new version)")
	@ApiResponse(responseCode = "400", description = "Invalid fields",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "403", description = "Not the site manager of the log's site",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No daily log (or child id) with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Stale version, or the log is signed off",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = {
					@ExampleObject(name = "stale version", value = ApiDocs.CONFLICT_VERSION),
					@ExampleObject(name = "signed off", value = ApiDocs.CONFLICT_LOCKED) }))
	@ApiResponse(responseCode = "422", description = "Duplicate or future date, inactive or repeated crew",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.UNPROCESSABLE_LOG)))
	public DailyLogResponse update(@PathVariable Long id, @Validated @RequestBody DailyLogUpdateRequest request) {
		dailyLogService.update(id, request.version(), request.toCommand());
		return DailyLogResponse.of(dailyLogService.getDetail(id));
	}

	@DeleteMapping("/daily-logs/{id}")
	@Operation(summary = "Delete a daily log", description = """
			Only a DRAFT log without incident or reopen history; its work entries and crew rows are \
			deleted with it.""")
	@ApiResponse(responseCode = "204", description = "Deleted")
	@ApiResponse(responseCode = "400", description = "Missing version parameter",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON))
	@ApiResponse(responseCode = "403", description = "Not the site manager of the log's site",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No daily log with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Stale version, not a draft, signed off, or linked to an incident",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.CONFLICT_VERSION)))
	public ResponseEntity<Void> delete(@PathVariable Long id,
			@Parameter(description = "Version of the daily log the client last read", required = true)
			@RequestParam Long version) {
		dailyLogService.delete(id, version);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/daily-logs/{id}/sign-off")
	@Operation(summary = "Sign off a daily log", description = """
			DRAFT or REOPENED -> SIGNED_OFF, recording who and when; from then on the log is locked \
			(AR1). Needs at least one work entry. Two concurrent sign-offs of the same version: the \
			first wins, the second gets 409.""")
	@ApiResponse(responseCode = "200", description = "The signed-off daily log")
	@ApiResponse(responseCode = "400", description = "Missing version",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "403", description = "Not the site manager of the log's site",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No daily log with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Stale version, already signed off, or no work entries",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.CONFLICT_VERSION)))
	public DailyLogResponse signOff(@PathVariable Long id, @Validated @RequestBody SignOffRequest request) {
		dailyLogService.signOff(id, request.version());
		return DailyLogResponse.of(dailyLogService.getDetail(id));
	}

}
