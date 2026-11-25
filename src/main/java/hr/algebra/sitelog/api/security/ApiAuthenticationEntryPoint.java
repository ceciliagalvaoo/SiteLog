package hr.algebra.sitelog.api.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Anonymous call to {@code /api/**}: 401 ProblemDetail. The screens keep the redirect to
 * {@code /login}; an API client cannot follow a redirect to an HTML form, so it gets a status
 * it can act on. {@code WWW-Authenticate} names the scheme the API uses (the session cookie
 * created by the form login) - there is no Basic or Bearer authentication.
 */
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

	static final String DETAIL = "Sign in first: POST /login (form login) creates the session the API uses.";

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "FormBased realm=\"SiteLog\"");
		ProblemJson.write(request, response, HttpStatus.UNAUTHORIZED, DETAIL, "error.401");
	}

}
