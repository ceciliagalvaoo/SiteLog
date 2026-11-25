package hr.algebra.sitelog.api.dailylog;

import java.net.URI;
import java.util.List;

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

import hr.algebra.sitelog.api.docs.ApiDocs;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.dailylog.service.CrewAssignmentService;
import hr.algebra.sitelog.dailylog.service.CrewAssignmentView;
import hr.algebra.sitelog.dailylog.service.DailyLogService;
import hr.algebra.sitelog.dailylog.service.WorkEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * {@code /api/v1/daily-logs/{logId}/work-entries/{entryId}/crew-assignments}: the crew rows of a
 * work entry ({@code CrewAssignment}, the association WorkEntry *-* Crew with hours, workers and
 * task). Same write rules as the work entries ({@code logVersion}, lock after sign-off).
 *
 * <p>The URL names the work entry; {@link #requireInEntry} answers 404 when a row id belongs to
 * another entry of the same log, so a URL never addresses a row that is not under it.
 */
@RestController
@RequestMapping("/api/v1/daily-logs/{logId}/work-entries/{entryId}/crew-assignments")
@Tag(name = "Crew assignments", description = "Crew rows of a work entry. Writing: the SITE_MANAGER of the log's site, while the log is not signed off.")
public class CrewAssignmentApiController {

	private final CrewAssignmentService crewAssignmentService;

	private final WorkEntryService workEntryService;

	private final DailyLogService dailyLogService;

	public CrewAssignmentApiController(CrewAssignmentService crewAssignmentService, WorkEntryService workEntryService,
			DailyLogService dailyLogService) {
		this.crewAssignmentService = crewAssignmentService;
		this.workEntryService = workEntryService;
		this.dailyLogService = dailyLogService;
	}

	@GetMapping
	@Operation(summary = "List the crew rows of a work entry", description = "Bounded part of the aggregate, not paged.")
	@ApiResponse(responseCode = "200", description = "The crew rows")
	@ApiResponse(responseCode = "404", description = "No such work entry in this log",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	public List<CrewAssignmentResponse> list(@PathVariable Long logId, @PathVariable Long entryId) {
		Long logVersion = dailyLogService.getHeader(logId).version();
		return workEntryService.get(logId, entryId).crewAssignments().stream()
			.map(row -> CrewAssignmentResponse.of(row, logVersion))
			.toList();
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a crew row")
	@ApiResponse(responseCode = "200", description = "The crew row")
	@ApiResponse(responseCode = "404", description = "No such row under this work entry",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	public CrewAssignmentResponse get(@PathVariable Long logId, @PathVariable Long entryId, @PathVariable Long id) {
		requireInEntry(logId, entryId, id);
		return response(logId, id);
	}

	@PostMapping
	@Operation(summary = "Add a crew row", description = "The crew must be active and not yet on this work entry.")
	@ApiResponse(responseCode = "201", description = "Created; Location points to the new row",
			headers = @Header(name = "Location", description = "URL of the new crew row"))
	@ApiResponse(responseCode = "400", description = "Invalid fields",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "403", description = "Not the site manager of the log's site",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No such work entry in this log",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Stale logVersion, or the log is signed off",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.CONFLICT_LOCKED)))
	@ApiResponse(responseCode = "422", description = "Inactive crew, or crew already on this entry",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.UNPROCESSABLE_CREW)))
	public ResponseEntity<CrewAssignmentResponse> create(@PathVariable Long logId, @PathVariable Long entryId,
			@Validated @RequestBody CrewAssignmentWriteRequest request) {
		Long id = crewAssignmentService.create(logId, entryId, request.logVersion(), request.toCommand());
		URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
			.path("/api/v1/daily-logs/{logId}/work-entries/{entryId}/crew-assignments/{id}")
			.buildAndExpand(logId, entryId, id)
			.toUri();
		return ResponseEntity.created(location).body(response(logId, id));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Replace a crew row")
	@ApiResponse(responseCode = "200", description = "The updated crew row")
	@ApiResponse(responseCode = "400", description = "Invalid fields",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "403", description = "Not the site manager of the log's site",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No such row under this work entry",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Stale logVersion, or the log is signed off",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.CONFLICT_LOCKED)))
	@ApiResponse(responseCode = "422", description = "Inactive crew, or crew already on this entry",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.UNPROCESSABLE_CREW)))
	public CrewAssignmentResponse update(@PathVariable Long logId, @PathVariable Long entryId, @PathVariable Long id,
			@Validated @RequestBody CrewAssignmentWriteRequest request) {
		requireInEntry(logId, entryId, id);
		crewAssignmentService.update(logId, id, request.logVersion(), request.toCommand());
		return response(logId, id);
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete a crew row")
	@ApiResponse(responseCode = "204", description = "Deleted")
	@ApiResponse(responseCode = "403", description = "Not the site manager of the log's site",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No such row under this work entry",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Stale logVersion, or the log is signed off",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.CONFLICT_LOCKED)))
	public ResponseEntity<Void> delete(@PathVariable Long logId, @PathVariable Long entryId, @PathVariable Long id,
			@Parameter(description = "Version of the daily log the client last read", required = true)
			@RequestParam Long logVersion) {
		requireInEntry(logId, entryId, id);
		crewAssignmentService.delete(logId, id, logVersion);
		return ResponseEntity.noContent().build();
	}

	/** The row plus the log's current version (incremented by the change just committed). */
	private CrewAssignmentResponse response(Long logId, Long id) {
		return CrewAssignmentResponse.of(crewAssignmentService.get(logId, id), dailyLogService.getHeader(logId).version());
	}

	/**
	 * URL consistency, not a business rule: the services address a row by log and row id; the
	 * entry in the URL must be the row's own entry, otherwise the resource does not exist here.
	 */
	private void requireInEntry(Long logId, Long entryId, Long id) {
		if (!entryId.equals(crewAssignmentService.workEntryIdOf(logId, id))) {
			throw new NotFoundException(CrewAssignmentView.class, id);
		}
	}

}
