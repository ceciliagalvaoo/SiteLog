package hr.algebra.sitelog.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.TestcontainersConfiguration;
import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.security.AppUserPrincipal;

/**
 * Form login against the real {@code app_user} seed rows (V4, BCrypt hashes of the demo
 * password): own login page, BCrypt check, inactive user refused, preferred language applied,
 * logout only as POST with the CSRF token.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoginFlowTests {

	/** Demo password of every seed user (documented in the README; not a real secret). */
	static final String DEMO_PASSWORD = "SiteLog-demo-2026";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void loginPageIsPublic() throws Exception {
		mvc.perform(get("/login"))
			.andExpect(status().isOk())
			.andExpect(view().name("auth/login"))
			.andExpect(content().string(not(containsString("Sign-in failed"))));
	}

	@Test
	void loginPageShowsTheErrorAndTheLogoutMessage() throws Exception {
		mvc.perform(get("/login").param("error", ""))
			.andExpect(content().string(containsString("Sign-in failed")));
		mvc.perform(get("/login").param("logout", ""))
			.andExpect(content().string(containsString("You have been signed out")));
	}

	@Test
	void seedUserSignsInWithTheDemoPasswordAndGetsTheRoleAuthority() throws Exception {
		mvc.perform(formLogin("/login").userParameter("email").user("admin@sitelog.test").password(DEMO_PASSWORD))
			.andExpect(authenticated().withUsername("admin@sitelog.test")
				.withRoles(RoleName.ADMIN.name()))
			.andExpect(redirectedUrl("/sites"));
	}

	@Test
	void loginAppliesThePreferredLanguage() throws Exception {
		mvc.perform(formLogin("/login").userParameter("email").user("manager@sitelog.test").password(DEMO_PASSWORD))
			.andExpect(authenticated())
			.andExpect(cookie().value("lang", "hr"));
	}

	@Test
	void emailIsCaseInsensitive() throws Exception {
		mvc.perform(formLogin("/login").userParameter("email").user("Inspector@SiteLog.test").password(DEMO_PASSWORD))
			.andExpect(authenticated().withUsername("inspector@sitelog.test"));
	}

	@Test
	void wrongPasswordGoesBackToTheLoginPageWithAnError() throws Exception {
		mvc.perform(formLogin("/login").userParameter("email").user("admin@sitelog.test").password("wrong-password"))
			.andExpect(unauthenticated())
			.andExpect(redirectedUrl("/login?error"));
	}

	@Test
	void inactiveUserCannotSignIn() throws Exception {
		jdbc.update("update app_user set active = false where email = 'inspector@sitelog.test'");

		mvc.perform(formLogin("/login").userParameter("email").user("inspector@sitelog.test").password(DEMO_PASSWORD))
			.andExpect(unauthenticated())
			.andExpect(redirectedUrl("/login?error"));
	}

	@Test
	void loginWithoutTheCsrfTokenIsRejected() throws Exception {
		mvc.perform(post("/login").param("email", "admin@sitelog.test").param("password", DEMO_PASSWORD))
			.andExpect(status().isForbidden())
			.andExpect(unauthenticated());
	}

	@Test
	@WithUserDetails("supervisor@sitelog.test")
	void signedInPrincipalIsTheAppUserWithItsRole() throws Exception {
		mvc.perform(get("/sites"))
			.andExpect(status().isOk())
			.andExpect(authenticated().withAuthenticationPrincipal(
					new AppUserPrincipal(id("supervisor@sitelog.test"), "supervisor@sitelog.test", null, "Ivana Babić",
							RoleName.SAFETY_SUPERVISOR, "en", true))
				.withRoles(RoleName.SAFETY_SUPERVISOR.name()));
	}

	@Test
	@WithUserDetails("supervisor@sitelog.test")
	void loginPageSendsASignedInUserToTheSites() throws Exception {
		mvc.perform(get("/login")).andExpect(redirectedUrl("/sites"));
	}

	@Test
	@WithUserDetails("supervisor@sitelog.test")
	void rootRedirectsToTheSites() throws Exception {
		mvc.perform(get("/")).andExpect(redirectedUrl("/sites"));
	}

	@Test
	@WithUserDetails("supervisor@sitelog.test")
	void logoutIsAPostWithTheCsrfToken() throws Exception {
		mvc.perform(logout("/logout"))
			.andExpect(unauthenticated())
			.andExpect(redirectedUrl("/login?logout"));
	}

	@Test
	@WithUserDetails("supervisor@sitelog.test")
	void logoutWithoutTheCsrfTokenIsRefused() throws Exception {
		mvc.perform(post("/logout")).andExpect(status().isForbidden());
	}

	@Test
	@WithUserDetails("supervisor@sitelog.test")
	void adminAreaIsForbiddenForOtherRoles() throws Exception {
		mvc.perform(get("/admin/users")).andExpect(status().isForbidden());
	}

	@Test
	void anonymousUserIsSentToTheLoginPage() throws Exception {
		mvc.perform(get("/sites"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	private long id(String email) {
		return jdbc.queryForObject("select id from app_user where email = ?", Long.class, email);
	}

}
