package hr.algebra.sitelog.user.web;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import hr.algebra.sitelog.user.security.PreferredLanguageLoginSuccessHandler;

/**
 * The login page and the start page. Only {@code GET /login} is ours: {@code POST /login}
 * (check the credentials) and {@code POST /logout} are handled by Spring Security's filters,
 * configured in {@code SecurityConfig}.
 *
 * <p>Query parameters read by the template: {@code ?error} (wrong e-mail / password, or an
 * inactive account - deliberately the same message) and {@code ?logout} (signed out).
 */
@Controller
public class LoginController {

	static final String LOGIN_VIEW = "auth/login";

	@GetMapping("/login")
	public String login(Authentication authentication) {
		if (isSignedIn(authentication)) {
			return "redirect:" + PreferredLanguageLoginSuccessHandler.DEFAULT_TARGET;
		}
		return LOGIN_VIEW;
	}

	/** The application has no separate home page: the sites list is the start screen. */
	@GetMapping("/")
	public String home() {
		return "redirect:" + PreferredLanguageLoginSuccessHandler.DEFAULT_TARGET;
	}

	private static boolean isSignedIn(Authentication authentication) {
		return authentication != null && authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken);
	}

}
