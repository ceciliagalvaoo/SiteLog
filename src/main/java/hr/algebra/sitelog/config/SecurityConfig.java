package hr.algebra.sitelog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.LocaleResolver;

import hr.algebra.sitelog.user.security.PreferredLanguageLoginSuccessHandler;

/**
 * Web security (URL level). The fine-grained rules - which role may call which operation, and
 * "only on the manager's own site" - are {@code @PreAuthorize} annotations on the services
 * ({@code MethodSecurityConfig}), so they also protect the REST API and any other caller.
 *
 * <ul>
 * <li><b>Public</b>: the login page, static assets of the layout, {@code /actuator/health}
 * (Docker and Render health checks) and {@code /error}.</li>
 * <li><b>{@code /admin/**}</b>: ADMIN only (user administration) - a second line of defence
 * next to the service annotations.</li>
 * <li><b>Crew forms</b> ({@code GET /crews/new}, {@code /crews/{id}/edit}) and every crew POST:
 * ADMIN only. The crew list stays readable by everyone.</li>
 * <li><b>Everything else</b>: any signed-in user.</li>
 * <li><b>Form login</b> on our own page {@code GET /login}; the form posts e-mail + password to
 * {@code POST /login}. Users come from {@code app_user} ({@code AppUserDetailsService}),
 * passwords are BCrypt hashes. A failed or inactive login goes back to {@code /login?error}.</li>
 * <li><b>Logout</b> is {@code POST /logout} with the CSRF token (a GET link cannot log anyone
 * out); it invalidates the session and goes to {@code /login?logout}.</li>
 * <li><b>CSRF</b> stays on (default): every Thymeleaf form with {@code th:action} gets the
 * hidden {@code _csrf} field automatically.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	public static final String LOGIN_PAGE = "/login";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, LocaleResolver localeResolver) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				// static assets of the layout (also needed by the login page)
				.requestMatchers("/css/**", "/js/**", "/img/**", "/favicon.ico").permitAll()
				.requestMatchers(LOGIN_PAGE, "/error").permitAll()
				.requestMatchers("/admin/**").hasRole("ADMIN")
				// crew forms and crew writes: refused before binding/validation, so a non-admin never
				// sees the form (not even re-rendered with errors); the service checks the writes again
				.requestMatchers(HttpMethod.GET, "/crews/new", "/crews/*/edit").hasRole("ADMIN")
				.requestMatchers(HttpMethod.POST, "/crews", "/crews/**").hasRole("ADMIN")
				.anyRequest().authenticated())
			.formLogin(form -> form
				.loginPage(LOGIN_PAGE)
				.usernameParameter("email")
				.passwordParameter("password")
				.successHandler(new PreferredLanguageLoginSuccessHandler(localeResolver))
				.failureUrl(LOGIN_PAGE + "?error")
				.permitAll())
			.logout(logout -> logout
				.logoutUrl("/logout")
				.logoutSuccessUrl(LOGIN_PAGE + "?logout")
				.invalidateHttpSession(true)
				.deleteCookies("JSESSIONID")
				.permitAll());
		return http.build();
	}

	/**
	 * BCrypt with the default strength (10, about 2^10 hashing rounds): slow enough to make
	 * guessing expensive, fast enough for a login. The seed users in V4 use the same algorithm.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
