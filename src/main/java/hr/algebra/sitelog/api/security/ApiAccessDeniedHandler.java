package hr.algebra.sitelog.api.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 403 raised in the filter chain for {@code /api/**} - in practice a POST / PUT / DELETE
 * without a valid CSRF token (the session-based API keeps CSRF protection on, see
 * {@link CsrfTokenController}). A 403 of {@code @PreAuthorize} on a service happens later,
 * inside the controller call, and is mapped by {@code ApiExceptionHandler}.
 */
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

	static final String CSRF_DETAIL = "Missing or invalid CSRF token. GET " + ApiRequests.CSRF_TOKEN_PATH
			+ " and send the token in the X-CSRF-TOKEN header.";

	static final String DENIED_DETAIL = "You do not have permission to perform this action.";

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		boolean csrf = accessDeniedException instanceof CsrfException;
		ProblemJson.write(request, response, HttpStatus.FORBIDDEN, csrf ? CSRF_DETAIL : DENIED_DETAIL,
				csrf ? "error.csrf" : "error.403.body");
	}

}
