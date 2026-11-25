package hr.algebra.sitelog.api.security;

import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** Request matchers of the REST API, used by {@code SecurityConfig}. */
public final class ApiRequests {

	/** Every REST endpoint: gets 401 / 403 as ProblemDetail instead of the login redirect / error page. */
	public static final String API_PATTERN = "/api/**";

	/** Public: hands out the CSRF token of the caller's session ({@link CsrfTokenController}). */
	public static final String CSRF_TOKEN_PATH = "/api/v1/csrf";

	public static final RequestMatcher API = PathPatternRequestMatcher.pathPattern(API_PATTERN);

	private ApiRequests() {
	}

}
