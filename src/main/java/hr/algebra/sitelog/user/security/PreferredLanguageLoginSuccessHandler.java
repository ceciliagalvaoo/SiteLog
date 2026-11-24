package hr.algebra.sitelog.user.security;

import java.io.IOException;
import java.util.Locale;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.web.servlet.LocaleResolver;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * After a successful login: switch the UI to the user's preferred language (stored in the
 * {@code lang} cookie by the application's {@link LocaleResolver}), then continue as usual - to
 * the page that asked for the login, or to {@code /sites}.
 */
public class PreferredLanguageLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

	public static final String DEFAULT_TARGET = "/sites";

	private final LocaleResolver localeResolver;

	public PreferredLanguageLoginSuccessHandler(LocaleResolver localeResolver) {
		this.localeResolver = localeResolver;
		setDefaultTargetUrl(DEFAULT_TARGET);
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws ServletException, IOException {
		if (authentication.getPrincipal() instanceof AppUserPrincipal principal
				&& principal.getPreferredLanguage() != null) {
			localeResolver.setLocale(request, response, Locale.forLanguageTag(principal.getPreferredLanguage()));
		}
		super.onAuthenticationSuccess(request, response, authentication);
	}

}
