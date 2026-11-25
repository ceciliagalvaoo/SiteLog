package hr.algebra.sitelog.api.incident;

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
import hr.algebra.sitelog.incident.domain.CorrectiveAction;
import hr.algebra.sitelog.incident.service.CorrectiveActionService;
import hr.algebra.sitelog.incident.service.IncidentDetails;
import hr.algebra.sitelog.incident.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * {@code /api/v1/incidents/{incidentId}/corrective-actions}: children of the Incident aggregate.
 * Every change increments the incident's version (OPTIMISTIC_FORCE_INCREMENT in
 * {@link CorrectiveActionService}), so the body carries {@code incidentVersion} and the response
 * returns the new one - the same contract as work entries of a daily log.
 *
 * <p>Reads go through {@link IncidentService#getDetail} (any signed-in user, the actions are
 * loaded with the incident in one query); the collection is bounded and not paged.
 */
@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/corrective-actions")
@Tag(name = "Corrective actions", description = """
		Corrective actions of an incident. Reading: any signed-in user. Adding, changing and deleting: \
		SAFETY_SUPERVISOR; marking as done: a supervisor or the action's assignee. Not on a CLOSED \
		incident; a DONE action is final.""")
public class CorrectiveActionApiController {

	private final CorrectiveActionService actionService;

	private final IncidentService incidentService;

	public CorrectiveActionApiController(CorrectiveActionService actionService, IncidentService incidentService) {
		this.actionService = actionService;
		this.incidentService = incidentService;
	}

	@GetMapping
	@Operation(summary = "List the corrective actions of an incident", description = "All actions of the incident (bounded, not paged).")
	@ApiResponse(responseCode = "200", description = "The corrective actions")
	@ApiResponse(responseCode = "404", description = "No incident with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	public List<CorrectiveActionResponse> list(@PathVariable Long incidentId) {
		IncidentDetails incident = incidentService.getDetail(incidentId);
		return incident.correctiveActions().stream()
			.map(action -> CorrectiveActionResponse.of(action, incident.version()))
			.toList();
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a corrective action")
	@ApiResponse(responseCode = "200", description = "The corrective action")
	@ApiResponse(responseCode = "404", description = "No such action on this incident",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	public CorrectiveActionResponse get(@PathVariable Long incidentId, @PathVariable Long id) {
		return response(incidentId, id);
	}

	@PostMapping
	@Operation(summary = "Add a corrective action", description = "SAFETY_SUPERVISOR only; the incident must not be CLOSED.")
	@ApiResponse(responseCode = "201", description = "Created (status OPEN); Location points to the new action",
			headers = @Header(name = "Location", description = "URL of the new corrective action"))
	@ApiResponse(responseCode = "400", description = "Invalid fields",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "403", description = "Not a safety supervisor",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No incident with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Stale incidentVersion, or the incident is closed",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.CONFLICT_INCIDENT_VERSION)))
	@ApiResponse(responseCode = "422", description = "Due date in the past, or assignee not an active manager / supervisor",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.UNPROCESSABLE_ACTION)))
	public ResponseEntity<CorrectiveActionResponse> create(@PathVariable Long incidentId,
			@Validated @RequestBody CorrectiveActionRequest request) {
		Long id = actionService.create(incidentId, request.incidentVersion(), request.toCommand());
		URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
			.path("/api/v1/incidents/{incidentId}/corrective-actions/{id}")
			.buildAndExpand(incidentId, id)
			.toUri();
		return ResponseEntity.created(location).body(response(incidentId, id));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Replace a corrective action", description = """
			SAFETY_SUPERVISOR only; not a DONE action. Moving the due date of an OVERDUE action to today \
			or later makes it OPEN again.""")
	@ApiResponse(responseCode = "200", description = "The updated action (with the new incidentVersion)")
	@ApiResponse(responseCode = "400", description = "Invalid fields",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "403", description = "Not a safety supervisor",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No such action on this incident",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Stale incidentVersion, incident closed, or action already done",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = {
					@ExampleObject(name = "stale version", value = ApiDocs.CONFLICT_INCIDENT_VERSION),
					@ExampleObject(name = "action done", value = ApiDocs.CONFLICT_ACTION_DONE) }))
	@ApiResponse(responseCode = "422", description = "Due date in the past, or invalid assignee",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.UNPROCESSABLE_ACTION)))
	public CorrectiveActionResponse update(@PathVariable Long incidentId, @PathVariable Long id,
			@Validated @RequestBody CorrectiveActionRequest request) {
		actionService.update(incidentId, id, request.incidentVersion(), request.toCommand());
		return response(incidentId, id);
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete a corrective action", description = "SAFETY_SUPERVISOR only; not a DONE action.")
	@ApiResponse(responseCode = "204", description = "Deleted")
	@ApiResponse(responseCode = "400", description = "Missing incidentVersion parameter",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON))
	@ApiResponse(responseCode = "403", description = "Not a safety supervisor",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No such action on this incident",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Stale incidentVersion, incident closed, or action already done",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.CONFLICT_ACTION_DONE)))
	public ResponseEntity<Void> delete(@PathVariable Long incidentId, @PathVariable Long id,
			@Parameter(description = "Version of the incident the client last read", required = true)
			@RequestParam Long incidentVersion) {
		actionService.delete(incidentId, id, incidentVersion);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/done")
	@Operation(summary = "Mark a corrective action as done", description = """
			OPEN / OVERDUE -> DONE with the completion time. A safety supervisor or the action's assignee.""")
	@ApiResponse(responseCode = "200", description = "The completed action (with the new incidentVersion)")
	@ApiResponse(responseCode = "400", description = "Missing incidentVersion",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "403", description = "Neither a safety supervisor nor the assignee",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No such action on this incident",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Stale incidentVersion, incident closed, or already done",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.CONFLICT_ACTION_DONE)))
	public CorrectiveActionResponse markDone(@PathVariable Long incidentId, @PathVariable Long id,
			@Validated @RequestBody CorrectiveActionDoneRequest request) {
		actionService.markDone(incidentId, id, request.incidentVersion());
		return response(incidentId, id);
	}

	/** The action plus the incident's current version (incremented by the change just committed). */
	private CorrectiveActionResponse response(Long incidentId, Long id) {
		IncidentDetails incident = incidentService.getDetail(incidentId);
		return incident.correctiveActions().stream()
			.filter(action -> action.id().equals(id))
			.findFirst()
			.map(action -> CorrectiveActionResponse.of(action, incident.version()))
			.orElseThrow(() -> new NotFoundException(CorrectiveAction.class, id));
	}

}
