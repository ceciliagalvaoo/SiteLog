package hr.algebra.sitelog.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.TestcontainersConfiguration;

/**
 * Profile and user administration against the real database: the pages render in both languages,
 * and the flows end where they matter - at the login (new user signs in, changed password works,
 * deactivated user cannot sign in).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserScreensTests {

	private static final String ADMIN = "admin@sitelog.test";

	private static final String MANAGER = "manager@sitelog.test";

	private static final String NEW_PASSWORD = "a-much-longer-passphrase";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	UserDetailsService userDetailsService;

	private long userId(String email) {
		return jdbc.queryForObject("select id from app_user where email = ?", Long.class, email);
	}

	@ParameterizedTest
	@ValueSource(strings = { "en", "hr" })
	void userPagesRenderInBothLanguages(String lang) throws Exception {
		render("/profile", lang, MANAGER);
		render("/profile", lang, ADMIN);
		for (String page : List.of("/admin/users", "/admin/users?q=marko", "/admin/users/new",
				"/admin/users/" + userId(MANAGER) + "/edit", "/admin/users/" + userId(ADMIN) + "/edit")) {
			render(page, lang, ADMIN);
		}
	}

	private void render(String page, String lang, String email) throws Exception {
		MvcResult result = mvc.perform(get(page).cookie(new Cookie("lang", lang))
				.with(user(userDetailsService.loadUserByUsername(email))))
			.andExpect(status().isOk())
			.andReturn();
		String html = result.getResponse().getContentAsString();
		if (html.contains("??")) {
			throw new AssertionError("Missing message key on " + page + " (" + lang + "): "
					+ html.substring(html.indexOf("??"), Math.min(html.length(), html.indexOf("??") + 80)));
		}
	}

	/** The login requests below must not carry the {@code @WithUserDetails} user of the test. */
	private static void signOutOfTheTestUser() {
		TestSecurityContextHolder.clearContext();
	}

	@Test
	@WithUserDetails("inspector@sitelog.test")
	void changedPasswordIsTheOneThatSignsIn() throws Exception {
		mvc.perform(post("/profile/password").with(csrf()).param("currentPassword", LoginFlowTests.DEMO_PASSWORD)
				.param("newPassword", NEW_PASSWORD).param("confirmPassword", NEW_PASSWORD))
			.andExpect(redirectedUrl("/profile"))
			.andExpect(flash().attribute("flashSuccess", "profile.flash.passwordChanged"));

		signOutOfTheTestUser();
		mvc.perform(formLogin("/login").userParameter("email").user("inspector@sitelog.test")
				.password(LoginFlowTests.DEMO_PASSWORD))
			.andExpect(unauthenticated());
		mvc.perform(formLogin("/login").userParameter("email").user("inspector@sitelog.test").password(NEW_PASSWORD))
			.andExpect(authenticated());
	}

	@Test
	@WithUserDetails(ADMIN)
	void userCreatedByTheAdminSignsInWithTheInitialPassword() throws Exception {
		mvc.perform(post("/admin/users").with(csrf()).param("email", "new.inspector@sitelog.test")
				.param("fullName", "New Inspector").param("role", "INSPECTOR").param("preferredLanguage", "en")
				.param("initialPassword", NEW_PASSWORD))
			.andExpect(status().is3xxRedirection());

		signOutOfTheTestUser();
		mvc.perform(formLogin("/login").userParameter("email").user("new.inspector@sitelog.test")
				.password(NEW_PASSWORD))
			.andExpect(authenticated().withRoles("INSPECTOR"));
	}

	@Test
	@WithUserDetails(ADMIN)
	void deactivatedUserCannotSignIn() throws Exception {
		mvc.perform(post("/admin/users/{id}/deactivate", userId("supervisor@sitelog.test")).with(csrf()))
			.andExpect(redirectedUrl("/admin/users"));

		signOutOfTheTestUser();
		mvc.perform(formLogin("/login").userParameter("email").user("supervisor@sitelog.test")
				.password(LoginFlowTests.DEMO_PASSWORD))
			.andExpect(unauthenticated());
	}

	@Test
	@WithUserDetails(ADMIN)
	void adminCannotDeactivateTheOwnAccount() throws Exception {
		mvc.perform(post("/admin/users/{id}/deactivate", userId(ADMIN)).with(csrf())
				.header("Referer", "http://localhost/admin/users"))
			.andExpect(redirectedUrl("/admin/users"))
			.andExpect(flash().attribute("flashError", "user.flash.selfDeactivate"));
	}

	@Test
	@WithUserDetails(MANAGER)
	void siteManagerCannotOpenTheUserAdministration() throws Exception {
		mvc.perform(get("/admin/users")).andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = { "en", "hr" })
	@WithUserDetails(MANAGER)
	void passwordErrorsAreShownOnTheProfileWithoutEchoingPasswords(String lang) throws Exception {
		mvc.perform(post("/profile/password").with(csrf()).cookie(new Cookie("lang", lang))
				.param("currentPassword", "not-my-password-at-all").param("newPassword", "short")
				.param("confirmPassword", "different"))
			.andExpect(status().isOk())
			.andExpect(view().name("user/profile"))
			.andExpect(content().string(containsString("en".equals(lang) ? "Password could not be changed"
					: "Lozinku nije moguće promijeniti")))
			.andExpect(content().string(containsString("newPassword-error")))
			.andExpect(content().string(not(containsString("not-my-password-at-all"))))
			.andExpect(content().string(not(containsString("??"))));
	}

	@Test
	@WithUserDetails(MANAGER)
	void profileShowsTheLanguageChoiceAndTheManagedSites() throws Exception {
		mvc.perform(get("/profile").cookie(new Cookie("lang", "en")))
			.andExpect(content().string(containsString("Personal details")))
			.andExpect(content().string(containsString("type=\"radio\"")))
			.andExpect(content().string(containsString("Croatian (HR)")))
			.andExpect(content().string(containsString("ZG-01")))
			.andExpect(content().string(containsString("readonly")));
	}

	@Test
	@WithUserDetails(ADMIN)
	void duplicateEmailIsShownOnTheNewUserForm() throws Exception {
		mvc.perform(post("/admin/users").with(csrf()).cookie(new Cookie("lang", "en")).param("email", MANAGER)
				.param("fullName", "Second Marko").param("role", "INSPECTOR").param("preferredLanguage", "en")
				.param("initialPassword", NEW_PASSWORD))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("User could not be saved")))
			.andExpect(content().string(containsString("Email is already used")))
			.andExpect(content().string(not(containsString(NEW_PASSWORD))));
	}

	@Test
	@WithUserDetails(ADMIN)
	void usersListLinksEveryRowAndKeepsTheSortInTheFilter() throws Exception {
		mvc.perform(get("/admin/users").param("sort", "email").cookie(new Cookie("lang", "en")))
			.andExpect(content().string(containsString("aria-sort=\"ascending\"")))
			.andExpect(content().string(containsString("name=\"sort\" value=\"email\"")))
			.andExpect(content().string(containsString("/admin/users/" + userId(MANAGER) + "/edit")))
			.andExpect(content().string(containsString("Open Marko Kovačević")));
	}

	@Test
	@WithUserDetails(ADMIN)
	void siteManagerFormOffersTheSiteAssignment() throws Exception {
		mvc.perform(get("/admin/users/{id}/edit", userId(MANAGER)).cookie(new Cookie("lang", "en")))
			.andExpect(content().string(containsString("name=\"siteIds\"")))
			.andExpect(content().string(containsString("Deactivate user")));
		mvc.perform(get("/admin/users/{id}/edit", userId("inspector@sitelog.test")).cookie(new Cookie("lang", "en")))
			.andExpect(content().string(not(containsString("name=\"siteIds\""))))
			.andExpect(content().string(containsString("Only active site managers can be assigned to sites.")));
	}

}
