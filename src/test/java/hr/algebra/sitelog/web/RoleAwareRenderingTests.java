package hr.algebra.sitelog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.TestcontainersConfiguration;

/**
 * The screens show each role only the actions it may perform (sec:authorize, #authorization and
 * the {@code siteAccess} bean in the templates). Hiding is only the visible half: the services
 * refuse the same calls with 403, which the security tests prove separately.
 *
 * <p>Seed users (V4): admin, manager (ZG-01, ZG-02), manager.split (ST-01), supervisor, inspector.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoleAwareRenderingTests {

	private static final String ADMIN = "admin@sitelog.test";

	private static final String MANAGER = "manager@sitelog.test";

	private static final String OTHER_MANAGER = "manager.split@sitelog.test";

	private static final String SUPERVISOR = "supervisor@sitelog.test";

	private static final String INSPECTOR = "inspector@sitelog.test";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	UserDetailsService userDetailsService;

	private long id(String sql, Object... args) {
		return jdbc.queryForObject(sql, Long.class, args);
	}

	private long siteId(String code) {
		return id("select id from site where code = ?", code);
	}

	/** A Draft log of ZG-02 without links: the manager may edit, sign off and delete it. */
	private long draftLog() {
		return id("select dl.id from daily_log dl join site s on s.id = dl.site_id"
				+ " where s.code = 'ZG-02' and dl.log_date = date '2026-10-28'");
	}

	private long userId(String email) {
		return id("select id from app_user where email = ?", email);
	}

	private String html(String url, String email) throws Exception {
		return mvc.perform(get(url).cookie(new Cookie("lang", "en"))
				.with(user(userDetailsService.loadUserByUsername(email))))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	// ------------------------------------------------------------------ menu and user menu

	@Test
	void adminMenuHasUsersAndCrews() throws Exception {
		String page = html("/sites", ADMIN);
		assertThat(page).contains("href=\"/admin/users\"", "href=\"/crews\"");
	}

	@ParameterizedTest
	@ValueSource(strings = { MANAGER, SUPERVISOR, INSPECTOR })
	void otherRolesHaveNoAdminMenu(String email) throws Exception {
		String page = html("/sites", email);
		assertThat(page).doesNotContain("href=\"/admin/users\"", "href=\"/crews\"");
	}

	@ParameterizedTest
	@ValueSource(strings = { ADMIN, MANAGER, SUPERVISOR, INSPECTOR })
	void everyRoleHasProfileAndLogout(String email) throws Exception {
		String page = html("/sites", email);
		assertThat(page).contains("href=\"/profile\"", "action=\"/logout\"", "name=\"_csrf\"")
			.doesNotContain("/account/profile");
	}

	// ------------------------------------------------------------------ sites

	@Test
	void onlyTheAdminCanCreateEditOrDeleteSites() throws Exception {
		long zg01 = siteId("ZG-01");
		assertThat(html("/sites", ADMIN)).contains("href=\"/sites/new\"");
		assertThat(html("/sites/" + zg01, ADMIN)).contains("href=\"/sites/" + zg01 + "/edit\"", "data-modal-open=\"delete-site\"");

		for (String email : new String[] { MANAGER, SUPERVISOR, INSPECTOR }) {
			assertThat(html("/sites", email)).doesNotContain("href=\"/sites/new\"");
			assertThat(html("/sites/" + zg01, email))
				.doesNotContain("href=\"/sites/" + zg01 + "/edit\"", "data-modal-open=\"delete-site\"", "id=\"delete-site\"");
		}
	}

	@Test
	void newDailyLogOnlyForTheManagerOfTheSite() throws Exception {
		long zg01 = siteId("ZG-01");
		String newLog = "/sites/" + zg01 + "/daily-logs/new";
		assertThat(html("/sites/" + zg01, MANAGER)).contains(newLog);
		// ADMIN does not write daily logs; the Split manager does not manage ZG-01
		for (String email : new String[] { ADMIN, OTHER_MANAGER, SUPERVISOR, INSPECTOR }) {
			assertThat(html("/sites/" + zg01, email)).as(email).doesNotContain(newLog);
			assertThat(html("/sites/" + zg01 + "?tab=logs", email)).as(email).doesNotContain(newLog);
		}
	}

	// ------------------------------------------------------------------ daily log detail

	@Test
	void managerOfTheSiteGetsTheEditingControls() throws Exception {
		long draft = draftLog();
		assertThat(html("/daily-logs/" + draft, MANAGER)).contains("/daily-logs/" + draft + "/edit",
				"/daily-logs/" + draft + "/sign-off", "/daily-logs/" + draft + "/delete",
				"/daily-logs/" + draft + "/work-entries/new");
	}

	@ParameterizedTest
	@ValueSource(strings = { ADMIN, OTHER_MANAGER, SUPERVISOR, INSPECTOR })
	void everyoneElseSeesTheDraftReadOnly(String email) throws Exception {
		long draft = draftLog();
		assertThat(html("/daily-logs/" + draft, email)).contains("Shift details")
			.doesNotContain("/daily-logs/" + draft + "/edit", "/daily-logs/" + draft + "/sign-off",
					"/daily-logs/" + draft + "/delete", "/daily-logs/" + draft + "/work-entries", "Remove entry",
					"crew-assignments");
	}

	// ------------------------------------------------------------------ crews

	@Test
	void crewChangesOnlyForTheAdmin() throws Exception {
		assertThat(html("/crews", ADMIN)).contains("href=\"/crews/new\"", "/delete\"", "/edit\"");
		for (String email : new String[] { MANAGER, INSPECTOR }) {
			assertThat(html("/crews", email)).as(email)
				.contains("Kovač Formwork")
				.doesNotContain("href=\"/crews/new\"", "/delete\"", "/edit\"");
		}
	}

	// ------------------------------------------------------------------ users

	@Test
	void adminCanDeactivateOthersButNotHimself() throws Exception {
		long manager = userId(MANAGER);
		long admin = userId(ADMIN);
		assertThat(html("/admin/users/" + manager + "/edit", ADMIN))
			.contains("action=\"/admin/users/" + manager + "/deactivate\"", ">Select a role<");
		assertThat(html("/admin/users/" + admin + "/edit", ADMIN))
			.doesNotContain("/deactivate\"", ">Select a role<")
			.contains("You cannot change your own role.");
	}

}
