package hr.algebra.sitelog.api.incident;

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
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.domain.Severity;
import hr.algebra.sitelog.incident.service.IncidentFilter;
import hr.algebra.sitelog.incident.service.IncidentService;
import hr.algebra.sitelog.site.service.SiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Incidents (AR2): the collection of a site ({@code /api/v1/sites/{siteId}/incidents}), the
 * collection of all sites ({@code /api/v1/incidents}) and one incident
 * ({@code /api/v1/incidents/{id}}). Every rule is in {@link IncidentService}: only the
 * SITE_MANAGER of the site reports (403), HIGH / CRITICAL need four extra fields (422 with the
 * missing ones) and a SAFETY_SUPERVISOR sign-off (403 for a manager), workflow order (409),
 * version check (409).
 *
 * <p>The workflow steps are {@code POST .../start-review|sign-off|close}: transitions with side
 * effects (who and when, notifications, audit), not a {@code status} field a client may PUT.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Incidents", description = """
		Safety incidents and the severity workflow REPORTED -> UNDER_REVIEW -> SIGNED_OFF -> CLOSED. \
		Reading: any signed-in user. Reporting: the SITE_MANAGER of the site. HIGH and CRITICAL need \
		root cause, witnesses, medical treatment and authority notified (422 otherwise) and are signed \
		off by a SAFETY_SUPERVISOR only (403 for anyone else).""")
public class IncidentApiController {

	static final Set<String> SORTABLE = Set.of("occurredAt", "severity", "status", "title");

	static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "occurredAt");

	private final IncidentService incidentService;

	private final SiteService siteService;

	public IncidentApiController(IncidentService incidentService, SiteService siteService) {
		this.incidentService = incidentService;
		this.siteService = siteService;
	}

	// ---------------------------------------------------------------- collections

	@GetMapping("/sites/{siteId}/incidents")
	@Operation(summary = "List the incidents of a site", description = """
			Paged and sorted (`?page=0&size=20&sort=occurredAt,desc`; size at most 100; sortable: \
			occurredAt, severity, status, title; default occurredAt descending). Optional filters \
			`status` and `severity`.""")
	@ApiResponse(responseCode = "200", description = "One page of incidents")
	@ApiResponse(responseCode = "400", description = "Unknown sort property or filter value",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "404", description = "No site with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	public PageResponse<IncidentSummaryResponse> listForSite(@PathVariable Long siteId,
			@RequestParam(required = false) IncidentStatus status, @RequestParam(required = false) Severity severity,
			@ParameterObject @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
		// 404 for an unknown site instead of an empty page
		siteService.getSummary(siteId);
		return page(new IncidentFilter(siteId, status, severity), pageable);
	}

	@GetMapping("/incidents")
	@Operation(summary = "List the incidents of all sites", description = """
			Same paging, sorting and filters as the site collection, plus an optional `siteId`.""")
	@ApiResponse(responseCode = "200", description = "One page of incidents")
	@ApiResponse(responseCode = "400", description = "Unknown sort property or filter value",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	public PageResponse<IncidentSummaryResponse> list(@RequestParam(required = false) Long siteId,
			@RequestParam(required = false) IncidentStatus status, @RequestParam(required = false) Severity severity,
			@ParameterObject @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return page(new IncidentFilter(siteId, status, severity), pageable);
	}

	@PostMapping("/sites/{siteId}/incidents")
	@Operation(summary = "Report an incident", description = """
			Only the SITE_MANAGER of this site. HIGH / CRITICAL: root cause, witnesses, medical treatment \
			and authority notified are required (422 lists the missing ones) and every safety supervisor \
			is notified.""")
	@ApiResponse(responseCode = "201", description = "Created (status REPORTED); Location points to the new incident",
			headers = @Header(name = "Location", description = "URL of the new incident"))
	@ApiResponse(responseCode = "400", description = "Invalid fields",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "403", description = "Not the site manager of this site",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No site with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "422", description = "Missing HIGH / CRITICAL fields, future date, daily log of another site, inactive crew",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = {
					@ExampleObject(name = "missing severity fields", value = ApiDocs.UNPROCESSABLE_SEVERITY),
					@ExampleObject(name = "future date", value = ApiDocs.UNPROCESSABLE_INCIDENT) }))
	public ResponseEntity<IncidentResponse> report(@PathVariable Long siteId,
			@Validated @RequestBody IncidentRequest request) {
		Long id = incidentService.report(siteId, request.toCommand());
		URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
			.path("/api/v1/incidents/{id}")
			.buildAndExpand(id)
			.toUri();
		return ResponseEntity.created(location).body(IncidentResponse.of(incidentService.getDetail(id)));
	}

	// ---------------------------------------------------------------- single incident

	@GetMapping("/incidents/{id}")
	@Operation(summary = "Get an incident", description = "The whole aggregate (crews, corrective actions, timeline) and its current version.")
	@ApiResponse(responseCode = "200", description = "The incident")
	@ApiResponse(responseCode = "404", description = "No incident with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	public IncidentResponse get(@PathVariable Long id) {
		return IncidentResponse.of(incidentService.getDetail(id));
	}

	@PutMapping("/incidents/{id}")
	@Operation(summary = "Replace an incident", description = """
			While REPORTED (site manager of the site or safety supervisor) or UNDER_REVIEW (safety \
			supervisor only). Only a supervisor may lower a HIGH / CRITICAL severity. `version` must be \
			the current one.""")
	@ApiResponse(responseCode = "200", description = "The updated incident (new version)")
	@ApiResponse(responseCode = "400", description = "Invalid fields",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "403", description = "Not the site manager of the incident's site nor a safety supervisor",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No incident with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Stale version, signed off / closed, or under review by a supervisor",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.CONFLICT_INCIDENT_VERSION)))
	@ApiResponse(responseCode = "422", description = "Missing HIGH / CRITICAL fields, severity lowered by a manager, future date",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.UNPROCESSABLE_SEVERITY)))
	public IncidentResponse update(@PathVariable Long id, @Validated @RequestBody IncidentUpdateRequest request) {
		incidentService.update(id, request.version(), request.toCommand());
		return IncidentResponse.of(incidentService.getDetail(id));
	}

	@DeleteMapping("/incidents/{id}")
	@Operation(summary = "Delete an incident", description = "Only a REPORTED incident without corrective actions, by the site manager of its site.")
	@ApiResponse(responseCode = "204", description = "Deleted")
	@ApiResponse(responseCode = "400", description = "Missing version parameter",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON))
	@ApiResponse(responseCode = "403", description = "Not the site manager of the incident's site",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No incident with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Stale version, not REPORTED, or has corrective actions",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.CONFLICT_INCIDENT_VERSION)))
	public ResponseEntity<Void> delete(@PathVariable Long id,
			@Parameter(description = "Version of the incident the client last read", required = true)
			@RequestParam Long version) {
		incidentService.delete(id, version);
		return ResponseEntity.noContent().build();
	}

	// ---------------------------------------------------------------- workflow

	@PostMapping("/incidents/{id}/start-review")
	@Operation(summary = "Start the review", description = """
			REPORTED -> UNDER_REVIEW. SAFETY_SUPERVISOR on any site; the site manager only for LOW / \
			MEDIUM incidents of his or her own site.""")
	@ApiResponse(responseCode = "200", description = "The incident under review")
	@ApiResponse(responseCode = "400", description = "Missing version",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "403", description = "HIGH / CRITICAL and not a safety supervisor, or another site",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No incident with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Stale version, or not REPORTED",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.CONFLICT_INCIDENT_VERSION)))
	public IncidentResponse startReview(@PathVariable Long id, @Validated @RequestBody IncidentVersionRequest request) {
		incidentService.startReview(id, request.version());
		return IncidentResponse.of(incidentService.getDetail(id));
	}

	@PostMapping("/incidents/{id}/sign-off")
	@Operation(summary = "Sign off an incident", description = """
			UNDER_REVIEW -> SIGNED_OFF, recording who and when. HIGH / CRITICAL: only a SAFETY_SUPERVISOR \
			(a site manager gets 403) and only with every extra field filled (422). Two concurrent \
			sign-offs of the same version: the first wins, the second gets 409.""")
	@ApiResponse(responseCode = "200", description = "The signed-off incident")
	@ApiResponse(responseCode = "400", description = "Missing version",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "403", description = "HIGH / CRITICAL and not a safety supervisor, or another site",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN_INCIDENT_SIGN_OFF)))
	@ApiResponse(responseCode = "404", description = "No incident with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Stale version, or not UNDER_REVIEW",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = {
					@ExampleObject(name = "stale version", value = ApiDocs.CONFLICT_INCIDENT_VERSION),
					@ExampleObject(name = "wrong state", value = ApiDocs.CONFLICT_INCIDENT_TRANSITION) }))
	@ApiResponse(responseCode = "422", description = "HIGH / CRITICAL fields still missing",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.UNPROCESSABLE_SEVERITY)))
	public IncidentResponse signOff(@PathVariable Long id, @Validated @RequestBody IncidentVersionRequest request) {
		incidentService.signOff(id, request.version());
		return IncidentResponse.of(incidentService.getDetail(id));
	}

	@PostMapping("/incidents/{id}/close")
	@Operation(summary = "Close an incident", description = "SIGNED_OFF -> CLOSED, only when every corrective action is DONE.")
	@ApiResponse(responseCode = "200", description = "The closed incident")
	@ApiResponse(responseCode = "400", description = "Missing version",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "403", description = "HIGH / CRITICAL and not a safety supervisor, or another site",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No incident with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "Stale version, not SIGNED_OFF, or open corrective actions",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = {
					@ExampleObject(name = "open corrective actions", value = ApiDocs.CONFLICT_INCIDENT_OPEN_ACTIONS),
					@ExampleObject(name = "stale version", value = ApiDocs.CONFLICT_INCIDENT_VERSION) }))
	public IncidentResponse close(@PathVariable Long id, @Validated @RequestBody IncidentVersionRequest request) {
		incidentService.close(id, request.version());
		return IncidentResponse.of(incidentService.getDetail(id));
	}

	private PageResponse<IncidentSummaryResponse> page(IncidentFilter filter, Pageable pageable) {
		Pageable page = ApiPaging.restrict(pageable, SORTABLE, DEFAULT_SORT);
		return PageResponse.of(incidentService.list(filter, page), IncidentSummaryResponse::of);
	}

}
