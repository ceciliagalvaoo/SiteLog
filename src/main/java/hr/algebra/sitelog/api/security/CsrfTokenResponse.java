package hr.algebra.sitelog.api.security;

import io.swagger.v3.oas.annotations.media.Schema;

/** The CSRF token of the caller's session and where to send it back. */
@Schema(description = "CSRF token of the current session")
public record CsrfTokenResponse(
		@Schema(description = "Request header that carries the token on POST / PUT / DELETE", example = "X-CSRF-TOKEN")
		String headerName,
		@Schema(description = "Form parameter alternative (used by the HTML forms)", example = "_csrf")
		String parameterName,
		@Schema(description = "Token value (masked; a new value on every call, all valid for the session)")
		String token) {
}
