package hr.algebra.sitelog.api.security;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * How an API client gets the CSRF token (decision 24/11: the REST API uses the same session and
 * the same CSRF protection as the screens, no exception in the filter chain).
 *
 * <p>The token stays in the HTTP session ({@code HttpSessionCsrfTokenRepository}, Spring
 * Security's default) - nothing changes for the Thymeleaf forms - and this endpoint hands it
 * out. Rejected alternative: {@code CookieCsrfTokenRepository} (an {@code XSRF-TOKEN} cookie
 * readable by JavaScript), which would change the token storage of every screen and expose the
 * token to any script on the page, only to save the client one GET.
 *
 * <p>Public on purpose (the client needs a token for {@code POST /login} too) and harmless: the
 * token is bound to the caller's own session, which an attacker's page cannot read
 * (same-origin policy), so handing it to the caller reveals nothing. Login rotates the token
 * (session fixation protection), so a client calls this again after signing in.
 */
@RestController
@Tag(name = "Authentication", description = "Session and CSRF token for API clients")
public class CsrfTokenController {

	@GetMapping(ApiRequests.CSRF_TOKEN_PATH)
	@SecurityRequirements
	@Operation(summary = "CSRF token of the current session", description = """
			Returns the CSRF token of the caller's session. Send it in the `X-CSRF-TOKEN` header on \
			every POST, PUT and DELETE (and as `_csrf` form field on `POST /login`). Signing in creates \
			a new token, so call this again after the login.""")
	@ApiResponse(responseCode = "200", description = "Token of the current session")
	public ResponseEntity<CsrfTokenResponse> csrf(@Parameter(hidden = true) CsrfToken token) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(new CsrfTokenResponse(token.getHeaderName(), token.getParameterName(), token.getToken()));
	}

}
