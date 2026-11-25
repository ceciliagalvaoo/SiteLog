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
import hr.algebra.sitelog.dailylog.service.DailyLogDetails;
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
 * {@code /api/v1/daily-logs/{logId}/work-entries}: one work entry (with its crew rows) at a time.
 * Every change increments the parent log's version (OPTIMISTIC_FORCE_INCREMENT in
 * {@link WorkEntryService}), so the body carries {@code logVersion} and the response returns the
 * new one.
 *
 * <p>The collection is not paged: the entries of one log are a small, bounded part of the
 * aggregate, loaded with the log in one query (the screen shows them all on the log detail).
 */
@RestController
@RequestMapping("/api/v1/daily-logs/{logId}/work-entries")
@Tag(name = "Work entries", description = "Work entries of a daily log. Writing: the SITE_MANAGER of the log's site, while the log is not signed off.")
public class WorkEntryApiController {

	private final WorkEntryService workEntryService;

	private final DailyLogService dailyLogService;

	public WorkEntryApiController(WorkEntryService workEntryService, DailyLogService dailyLogService) {
		this.workEntryService = workEntryService;
		this.dailyLogService = dailyLogService;
	}

	@GetMapping
	@Operation(summary = "List the work entries of a daily log", description = "All entries of the log with their crew rows (bounded, not paged).")
	@ApiResponse(responseCode = "200", description = "The work entries")
	@ApiResponse(responseCode = "404", description = "No daily log with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	public List<WorkEntryResponse> list(@PathVariable Long logId) {
		DailyLogDetails log = dailyLogService.getDetail(logId);
		return log.workEntries().stream().map(entry -> WorkEntryResponse.of(entry, log.version())).toList();
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a work entry")
	@ApiResponse(responseCode = "200", description = "The work entry")
	@ApiResponse(responseCode = "404", description = "No such entry in this log",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	public WorkEntryResponse get(@PathVariable Long logId, @PathVariable Long id) {
		return response(logId, id);
	}

	@PostMapping
	@Operation(summary = "Add a work entry", description = "Adds an entry (with its crew rows) to a log that is not signed off.")
	@ApiResponse(responseCode = "201", description = "Created; Location points to the new entry",
			headers = @Header(name = "Location", description = "URL of the new work entry"))
	@ApiResponse(responseCode = "400", description = "Invalid fields",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "403", description = "Not the site manager of the log's site",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No daily log with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Stale logVersion, or the log is signed off",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = {
					@ExampleObject(name = "stale version", value = ApiDocs.CONFLICT_VERSION),
					@ExampleObject(name = "signed off", value = ApiDocs.CONFLICT_LOCKED) }))
	@ApiResponse(responseCode = "422", description = "Inactive or repeated crew",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.UNPROCESSABLE_CREW)))
	public ResponseEntity<WorkEntryResponse> create(@PathVariable Long logId,
			@Validated @RequestBody WorkEntryWriteRequest request) {
		Long id = workEntryService.create(logId, request.logVersion(), request.toCommand());
		URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
			.path("/api/v1/daily-logs/{logId}/work-entries/{id}")
			.buildAndExpand(logId, id)
			.toUri();
		return ResponseEntity.created(location).body(response(logId, id));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Replace a work entry", description = "Crew rows with id: changed, without id: added, left out: removed.")
	@ApiResponse(responseCode = "200", description = "The updated work entry (with the new logVersion)")
	@ApiResponse(responseCode = "400", description = "Invalid fields",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "403", description = "Not the site manager of the log's site",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No such entry in this log",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Stale logVersion, or the log is signed off",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.CONFLICT_LOCKED)))
	@ApiResponse(responseCode = "422", description = "Inactive or repeated crew",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.UNPROCESSABLE_CREW)))
	public WorkEntryResponse update(@PathVariable Long logId, @PathVariable Long id,
			@Validated @RequestBody WorkEntryWriteRequest request) {
		workEntryService.update(logId, id, request.logVersion(), request.toCommand());
		return response(logId, id);
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete a work entry", description = "Removes the entry and its crew rows.")
	@ApiResponse(responseCode = "204", description = "Deleted")
	@ApiResponse(responseCode = "403", description = "Not the site manager of the log's site",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No such entry in this log",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Stale logVersion, or the log is signed off",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.CONFLICT_LOCKED)))
	public ResponseEntity<Void> delete(@PathVariable Long logId, @PathVariable Long id,
			@Parameter(description = "Version of the daily log the client last read", required = true)
			@RequestParam Long logVersion) {
		workEntryService.delete(logId, id, logVersion);
		return ResponseEntity.noContent().build();
	}

	/** The entry plus the log's current version (incremented by the change just committed). */
	private WorkEntryResponse response(Long logId, Long id) {
		return WorkEntryResponse.of(workEntryService.get(logId, id), dailyLogService.getHeader(logId).version());
	}

}
