package hr.algebra.sitelog.api.site;

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
import hr.algebra.sitelog.site.service.SiteService;
import hr.algebra.sitelog.site.service.SiteStatusFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * {@code /api/v1/sites}: the root of the aggregate. Thin like the screen controller: request
 * record -> {@code SiteCommand} -> {@link SiteService} -> response record. The service decides
 * everything else: Admin-only writes ({@code @PreAuthorize} -> 403), unique code and active
 * manager (-> 422), delete only without records (-> 409), not found (-> 404).
 */
@RestController
@RequestMapping("/api/v1/sites")
@Tag(name = "Sites", description = "Construction sites (root of the aggregate). Reading: any signed-in user; writing: ADMIN.")
public class SiteApiController {

	static final Set<String> SORTABLE = Set.of("code", "name", "startDate");

	private final SiteService siteService;

	public SiteApiController(SiteService siteService) {
		this.siteService = siteService;
	}

	@GetMapping
	@Operation(summary = "List sites", description = """
			Paged and sorted (`?page=0&size=20&sort=name,asc`; size at most 100; sortable: code, name, \
			startDate; default name). Optional filters: `q` (code or name contains, case-insensitive) and \
			`status` (ALL, ACTIVE, FINISHED).""")
	@ApiResponse(responseCode = "200", description = "One page of sites")
	@ApiResponse(responseCode = "400", description = "Unknown sort property or status value",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	public PageResponse<SiteSummaryResponse> list(
			@Parameter(description = "Text contained in the code or name") @RequestParam(required = false) String q,
			@Parameter(description = "Project status") @RequestParam(defaultValue = "ALL") SiteStatusFilter status,
			@ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
		Pageable page = ApiPaging.restrict(pageable, SORTABLE, Sort.by("name"));
		return PageResponse.of(siteService.list(q, status, page), SiteSummaryResponse::of);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a site", description = "The site, its manager and the overview counters.")
	@ApiResponse(responseCode = "200", description = "The site")
	@ApiResponse(responseCode = "404", description = "No site with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	public SiteResponse get(@PathVariable Long id) {
		return SiteResponse.of(siteService.get(id));
	}

	@PostMapping
	@Operation(summary = "Create a site", description = "ADMIN only.")
	@ApiResponse(responseCode = "201", description = "Created; Location points to the new site",
			headers = @Header(name = "Location", description = "URL of the new site"))
	@ApiResponse(responseCode = "400", description = "Invalid fields",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "403", description = "Not an ADMIN",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "422", description = "Code already used, or the manager is not an active site manager",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.UNPROCESSABLE_SITE)))
	public ResponseEntity<SiteResponse> create(@Validated @RequestBody SiteRequest request) {
		Long id = siteService.create(request.toCommand());
		URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
			.path("/api/v1/sites/{id}")
			.buildAndExpand(id)
			.toUri();
		return ResponseEntity.created(location).body(SiteResponse.of(siteService.get(id)));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Replace a site", description = "ADMIN only. Every field is replaced (setting endDate finishes the site).")
	@ApiResponse(responseCode = "200", description = "The updated site")
	@ApiResponse(responseCode = "400", description = "Invalid fields",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.BAD_REQUEST)))
	@ApiResponse(responseCode = "403", description = "Not an ADMIN",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No site with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "422", description = "Code already used, or the manager is not an active site manager",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.UNPROCESSABLE_SITE)))
	public SiteResponse update(@PathVariable Long id, @Validated @RequestBody SiteRequest request) {
		siteService.update(id, request.toCommand());
		return SiteResponse.of(siteService.get(id));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete a site", description = """
			ADMIN only, and only while the site has no daily logs, incidents or AI drafts; otherwise \
			finish it by setting an end date.""")
	@ApiResponse(responseCode = "204", description = "Deleted")
	@ApiResponse(responseCode = "403", description = "Not an ADMIN",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.FORBIDDEN)))
	@ApiResponse(responseCode = "404", description = "No site with this id",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.NOT_FOUND)))
	@ApiResponse(responseCode = "409", description = "The site holds records",
			content = @Content(mediaType = ApiDocs.PROBLEM_JSON, examples = @ExampleObject(value = ApiDocs.CONFLICT_SITE_DELETE)))
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		siteService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
