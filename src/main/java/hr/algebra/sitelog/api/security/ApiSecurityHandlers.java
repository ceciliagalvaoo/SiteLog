package hr.algebra.sitelog.api.security;

import java.util.LinkedHashMap;

import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.access.RequestMatcherDelegatingAccessDeniedHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * The two exception handlers of the filter chain, split by path: {@code /api/**} gets
 * ProblemDetail JSON ({@link ApiAuthenticationEntryPoint}, {@link ApiAccessDeniedHandler}),
 * every other URL keeps the behaviour of the screens (redirect to the login page, the
 * {@code error/403} page).
 *
 * <p>Set explicitly with {@code authenticationEntryPoint(...)} / {@code accessDeniedHandler(...)}
 * rather than {@code defaultAuthenticationEntryPointFor(...)}: with the "default for" variants
 * Spring Security uses the first registered handler as the fallback for requests no matcher
 * claims, so an API handler registered first would also answer screen requests (a 401 instead
 * of the login redirect for a non-HTML {@code Accept} header).
 */
public final class ApiSecurityHandlers {

	private ApiSecurityHandlers() {
	}

	/** 401 ProblemDetail for {@code /api/**}, redirect to {@code loginPage} for everything else. */
	public static AuthenticationEntryPoint authenticationEntryPoint(String loginPage) {
		return DelegatingAuthenticationEntryPoint.builder()
			.addEntryPointFor(new ApiAuthenticationEntryPoint(), ApiRequests.API)
			.defaultEntryPoint(new LoginUrlAuthenticationEntryPoint(loginPage))
			.build();
	}

	/** 403 ProblemDetail for {@code /api/**} (e.g. missing CSRF token), the error page elsewhere. */
	public static AccessDeniedHandler accessDeniedHandler() {
		LinkedHashMap<RequestMatcher, AccessDeniedHandler> handlers = new LinkedHashMap<>();
		handlers.put(ApiRequests.API, new ApiAccessDeniedHandler());
		return new RequestMatcherDelegatingAccessDeniedHandler(handlers, new AccessDeniedHandlerImpl());
	}

}
