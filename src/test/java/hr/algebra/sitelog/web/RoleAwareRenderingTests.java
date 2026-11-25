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
 * the {@code siteAccess} / {@code incidentAccess} beans in the templates). Hiding is only the visible half: the services
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

	// ------------------------------------------------------------------ navigation (M3-06)

	@Test
	void reopenQueueLinkOnlyForTheSafetySupervisor() throws Exception {
		assertThat(html("/sites", SUPERVISOR)).contains("href=\"/reopen-requests\"");
		for (String email : new String[] { ADMIN, MANAGER, INSPECTOR }) {
			assertThat(html("/sites", email)).as(email).doesNotContain("href=\"/reopen-requests\"");
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { ADMIN, MANAGER, SUPERVISOR, INSPECTOR })
	void everyRoleHasTheIncidentsLinkAndTheSiteTabLeadsToTheIncidentList(String email) throws Exception {
		long zg01 = siteId("ZG-01");
		assertThat(html("/sites/" + zg01, email)).contains("href=\"/incidents\"",
				"href=\"/sites/" + zg01 + "/incidents\"");
	}

	// ------------------------------------------------------------------ incidents (AR2)

	private long incidentId(String title) {
		return id("select id from incident where title = ?", title);
	}

	/** Fills the AR2 fields of the seed HIGH incident under review on ST-01, so sign-off is possible. */
	private long completeHighIncidentUnderReview() {
		long scaffold = incidentId("Scaffold tube dropped from level 4");
		jdbc.update("update incident set root_cause = 'Toe board missing', medical_treatment = 'None',"
				+ " authority_notified = false where id = ?", scaffold);
		return scaffold;
	}

	@Test
	void onlyTheSupervisorSignsOffAHighIncident() throws Exception {
		long scaffold = completeHighIncidentUnderReview();
		String signOff = "action=\"/incidents/" + scaffold + "/sign-off\"";
		// the Split manager reported it on his own site, but High needs a safety supervisor
		assertThat(html("/incidents/" + scaffold, OTHER_MANAGER)).doesNotContain(signOff)
			.contains("is available to Safety Supervisors only.");
		String supervisorView = html("/incidents/" + scaffold, SUPERVISOR);
		assertThat(supervisorView).contains(signOff);
		String form = supervisorView.substring(supervisorView.indexOf(signOff));
		assertThat(form.substring(0, form.indexOf("</form>"))).doesNotContain("disabled");
	}

	@Test
	void managerHandlesALowIncidentOfHisOwnSite() throws Exception {
		long low = incidentId("Worker slipped on wet stairs");
		assertThat(html("/incidents/" + low, MANAGER)).contains("action=\"/incidents/" + low + "/start-review\"",
				"href=\"/incidents/" + low + "/edit\"", "data-modal-open=\"delete-incident\"");
		// another site's manager only reads it
		assertThat(html("/incidents/" + low, OTHER_MANAGER)).doesNotContain("/start-review\"", "/edit\"",
				"delete-incident");
	}

	@Test
	void inspectorSeesNoIncidentActions() throws Exception {
		for (String title : new String[] { "Worker slipped on wet stairs", "Back strain while unloading blocks",
				"Fall from slab edge", "Scaffold tube dropped from level 4" }) {
			long incident = incidentId(title);
			assertThat(html("/incidents/" + incident, INSPECTOR)).as(title)
				.contains("Status timeline")
				.doesNotContain("/start-review\"", "/sign-off\"", "/close\"", "/edit\"", "/delete\"",
						"corrective-actions/new", "/done\"");
		}
	}

	@Test
	void correctiveActionsAreManagedBySupervisorAndCompletedByTheAssignee() throws Exception {
		long fall = incidentId("Fall from slab edge");
		String addAction = "/incidents/" + fall + "/corrective-actions/new";
		// the open action is assigned to the supervisor; the manager's own action is already done
		assertThat(html("/incidents/" + fall, SUPERVISOR)).contains(addAction, "/done\"", "/edit\"");
		assertThat(html("/incidents/" + fall, MANAGER)).doesNotContain(addAction, "/done\"");

		long open = id("select id from corrective_action where incident_id = ? and status = 'OPEN'", fall);
		jdbc.update("update corrective_action set assignee_id = ? where id = ?", userId(MANAGER), open);
		assertThat(html("/incidents/" + fall, MANAGER)).contains("corrective-actions/" + open + "/done\"")
			.doesNotContain(addAction, "corrective-actions/" + open + "/edit\"");
	}

	@Test
	void reportIncidentOnlyForTheManagerOfTheSite() throws Exception {
		long zg01 = siteId("ZG-01");
		String report = "href=\"/sites/" + zg01 + "/incidents/new\"";
		assertThat(html("/sites/" + zg01 + "/incidents", MANAGER)).contains(report);
		for (String email : new String[] { ADMIN, OTHER_MANAGER, SUPERVISOR, INSPECTOR }) {
			assertThat(html("/sites/" + zg01 + "/incidents", email)).as(email).doesNotContain(report);
		}
	}

	// ------------------------------------------------------------------ reopen (AR1)

	private long log(String code, String date) {
		return id("select dl.id from daily_log dl join site s on s.id = dl.site_id where s.code = ?"
				+ " and dl.log_date = cast(? as date)", code, date);
	}

	@Test
	void onlyTheManagerOfTheSiteRequestsAReopen() throws Exception {
		long signed = log("ZG-02", "2026-10-27");
		assertThat(html("/daily-logs/" + signed, MANAGER)).contains("data-modal-open=\"reopen-modal\"",
				"action=\"/daily-logs/" + signed + "/reopen-requests\"");
		for (String email : new String[] { ADMIN, OTHER_MANAGER, SUPERVISOR, INSPECTOR }) {
			assertThat(html("/daily-logs/" + signed, email)).as(email)
				.contains("Signed off and locked")
				.doesNotContain("reopen-modal", "action=\"/daily-logs/" + signed + "/reopen-requests\"");
		}
	}

	@Test
	void onlyTheSupervisorDecidesAPendingReopen() throws Exception {
		long pending = log("ST-01", "2026-10-27");
		long request = id("select id from log_reopen_request where daily_log_id = ? and status = 'PENDING'", pending);
		String approve = "/daily-logs/" + pending + "/reopen-requests/" + request + "/approve";
		assertThat(html("/daily-logs/" + pending, SUPERVISOR)).contains(approve, "data-modal-open=\"reject-modal\"",
				"/reopen-requests/" + request + "/reject");
		// the requester (Split manager) and everyone else only see that it is pending
		for (String email : new String[] { ADMIN, OTHER_MANAGER, MANAGER, INSPECTOR }) {
			assertThat(html("/daily-logs/" + pending, email)).as(email)
				.contains("chip--reopen-pending")
				.doesNotContain(approve, "reject-modal", "data-modal-open=\"reopen-modal\"");
		}
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
