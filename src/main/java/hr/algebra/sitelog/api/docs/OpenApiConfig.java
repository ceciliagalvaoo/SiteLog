package hr.algebra.sitelog.api.docs;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import hr.algebra.sitelog.api.error.FieldViolation;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * The OpenAPI 3.1 document ({@code /v3/api-docs}, Swagger UI at {@code /swagger-ui.html}, both
 * for signed-in users only). Describes how a client authenticates (session cookie from the form
 * login) and obtains the CSRF token, and adds the responses every operation can return
 * (401, 403 and 500 as ProblemDetail) so they are not repeated on each endpoint.
 *
 * <p>Swagger UI is a convenience for signed-in users, not a public contract page: the document
 * lists every write operation, so it stays behind the login like the rest of the application
 * (no extra rule in {@code SecurityConfig}: {@code anyRequest().authenticated()} covers it).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	static final String SESSION = "session";

	static final String CSRF = "csrf";

	static final String PROBLEM_SCHEMA = "ProblemDetail";

	private static final Set<PathItem.HttpMethod> UNSAFE = Set.of(PathItem.HttpMethod.POST, PathItem.HttpMethod.PUT,
			PathItem.HttpMethod.PATCH, PathItem.HttpMethod.DELETE);

	static final String DESCRIPTION = """
			REST API of SiteLog: the Site -> DailyLog -> WorkEntry -> Crew aggregate, the audited \
			reopen of signed-off logs (AR1) and the Incident -> CorrectiveAction aggregate with its \
			severity workflow (AR2).

			**Authentication.** The API uses the same session as the web screens (no tokens, no Basic):
			1. `GET /api/v1/csrf` (public) - returns a CSRF token and creates a session (`JSESSIONID` cookie).
			2. `POST /login` as a form (`email`, `password`, `_csrf` = that token) - signs the session in.
			3. `GET /api/v1/csrf` again - signing in rotates the token.
			4. Send the cookie on every call and the token in the `X-CSRF-TOKEN` header on every POST, PUT \
			and DELETE. Without it the call is refused with 403.

			In Swagger UI the browser already sends the session cookie of the signed-in user: call \
			`GET /api/v1/csrf`, copy `token`, click **Authorize** and paste it into `csrf`.

			**Authorization** is the one of the screens (enforced in the services): every signed-in user \
			reads; ADMIN writes sites; the SITE_MANAGER of a site writes its daily logs, reports its \
			incidents and requests reopens; the SAFETY_SUPERVISOR decides reopens, signs off HIGH / \
			CRITICAL incidents and manages corrective actions.

			**Concurrency.** Daily logs and incidents carry a `version`; writes send the version the \
			client read (`version` / `logVersion` / `incidentVersion`) and get 409 when someone else \
			changed the record in the meantime.

			**Errors** are RFC 9457 `application/problem+json` with a stable `code` (message key) and, for \
			400 / 422, an `errors` list of fields. Texts follow the UI language (`lang` cookie, en / hr).""";

	@Bean
	OpenAPI siteLogOpenApi() {
		return new OpenAPI()
			.info(new Info().title("SiteLog API").version("v1").description(DESCRIPTION))
			.components(new Components()
				.addSecuritySchemes(SESSION, new SecurityScheme()
					.type(SecurityScheme.Type.APIKEY)
					.in(SecurityScheme.In.COOKIE)
					.name("JSESSIONID")
					.description("Session cookie created by the form login (POST /login)"))
				.addSecuritySchemes(CSRF, new SecurityScheme()
					.type(SecurityScheme.Type.APIKEY)
					.in(SecurityScheme.In.HEADER)
					.name("X-CSRF-TOKEN")
					.description("CSRF token from GET /api/v1/csrf; required on POST, PUT and DELETE")))
			.addSecurityItem(new SecurityRequirement().addList(SESSION));
	}

	/**
	 * Adds the CSRF requirement to every unsafe operation and the 401 / 403 / 500 ProblemDetail
	 * responses to every operation (unless the endpoint documents its own 403).
	 */
	@Bean
	OpenApiCustomizer commonApiResponses() {
		return openApi -> {
			openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemSchema(openApi));
			Content problem = new Content().addMediaType(ApiDocs.PROBLEM_JSON,
					new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA)));
			openApi.getPaths().values().forEach(path -> path.readOperationsMap().forEach((method, operation) -> {
				if (UNSAFE.contains(method)) {
					requireCsrf(operation);
				}
				var responses = operation.getResponses();
				// error responses documented on the endpoints carry examples only: point them to the schema
				responses.values().stream()
					.filter(response -> response.getContent() != null)
					.map(response -> response.getContent().get(ApiDocs.PROBLEM_JSON))
					.filter(media -> media != null && media.getSchema() == null)
					.forEach(media -> media.setSchema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA)));
				if (operation.getSecurity() == null || !operation.getSecurity().isEmpty()) {
					responses.putIfAbsent("401", new ApiResponse().description("Not signed in").content(problem));
				}
				responses.putIfAbsent("403", new ApiResponse()
					.description("Role not allowed, or missing CSRF token on POST / PUT / DELETE")
					.content(problem));
				responses.putIfAbsent("500", new ApiResponse().description("Unexpected error").content(problem));
			}));
		};
	}

	/**
	 * The RFC 9457 members plus our extensions ({@code code}, {@code errors}), instead of the
	 * schema derived from Spring's {@code ProblemDetail} class, whose extension map would show up
	 * as a nested {@code properties} object that never appears on the wire.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Schema problemSchema(OpenAPI openApi) {
		Map<String, Schema> violation = ModelConverters.getInstance().read(FieldViolation.class);
		violation.forEach(openApi.getComponents()::addSchemas);
		return new ObjectSchema()
			.description("RFC 9457 problem details (application/problem+json)")
			.addProperty("type", new StringSchema().format("uri").example("about:blank"))
			.addProperty("title", new StringSchema().example("Conflict"))
			.addProperty("status", new IntegerSchema().example(409))
			.addProperty("detail", new StringSchema().description("Text in the UI language (en / hr)"))
			.addProperty("instance", new StringSchema().format("uri").example("/api/v1/daily-logs/42"))
			.addProperty("code", new StringSchema().description("Stable message key").example("dailyLog.flash.locked"))
			.addProperty("errors", new ArraySchema()
				.description("Invalid fields (400 and 422 only)")
				.items(new Schema<>().$ref("#/components/schemas/FieldViolation")))
			.required(List.of("type", "title", "status"));
	}

	private static void requireCsrf(Operation operation) {
		if (operation.getSecurity() != null && operation.getSecurity().isEmpty()) {
			return;
		}
		operation.addSecurityItem(new SecurityRequirement().addList(SESSION).addList(CSRF));
	}

}
