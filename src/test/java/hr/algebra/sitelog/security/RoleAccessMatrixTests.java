package hr.algebra.sitelog.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.TestcontainersConfiguration;

/**
 * Access matrix of the main screens, per role, through the REAL filter chain, the real
 * {@code @PreAuthorize} rules and the V4 seed users (Testcontainers Postgres + Flyway).
 *
 * <p>Every (endpoint, user) pair is one test case: a user in the allowed set must get the normal
 * answer (200 for a page, a redirect for a POST - the POST-Redirect-GET contract), every other
 * user must get exactly 403. POSTs carry a valid CSRF token and a valid body, so a 403 can only
 * come from the authorization rule, never from CSRF or validation. Each case runs in a rolled-back
 * transaction, so the allowed POSTs do not leak data into the next case.
 *
 * <p>Users: {@code manager@} manages ZG-01 and ZG-02, {@code manager.split@} manages ST-01.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoleAccessMatrixTests {

	static final String ADMIN = "admin@sitelog.test";

	static final String MANAGER_ZG = "manager@sitelog.test";

	static final String MANAGER_ST = "manager.split@sitelog.test";

	static final String SUPERVISOR = "supervisor@sitelog.test";

	static final String INSPECTOR = "inspector@sitelog.test";

	static final Set<String> EVERYONE = Set.of(ADMIN, MANAGER_ZG, MANAGER_ST, SUPERVISOR, INSPECTOR);

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	UserDetailsService users;

	private Ids ids;

	/** Seed ids, looked up once per case (the ids depend on the sequence, never hard-coded). */
	record Ids(long zg01, long st01, long zgDraftLog, long zgDraftLogVersion, long zg02,
			long managerZg, long inspector, long crew, Incidents incidents, Reopens reopens) {
	}

	/**
	 * Seed incidents used by the M3-06 rows: a LOW REPORTED one on ZG-01, the HIGH UNDER_REVIEW one
	 * on ST-01 (its AR2 fields are filled in {@link #loadSeedIds}, inside the rolled-back
	 * transaction, so a sign-off can succeed), the HIGH SIGNED_OFF one on ZG-01, and the MEDIUM
	 * ZG-02 one with an OPEN action assigned to {@code manager@}.
	 */
	record Incidents(long zgLow, long zgLowVersion, long stHigh, long stHighVersion, long zgSigned,
			long zgSignedVersion, long backStrain, long backStrainVersion, long backStrainAction) {
	}

	/** A ZG-01 log signed off without any reopen request, and the PENDING request of ST-01. */
	record Reopens(long zgSignedLog, long zgSignedLogVersion, long stPendingLog, long stPendingRequest) {
	}

	@BeforeEach
	void loadSeedIds() {
		long zgDraftLog = id("""
				select dl.id from daily_log dl join site s on s.id = dl.site_id
				where s.code = 'ZG-01' and dl.log_date = date '2026-10-29' and dl.status = 'DRAFT'""");
		ids = new Ids(id("select id from site where code = 'ZG-01'"), id("select id from site where code = 'ST-01'"),
				zgDraftLog, id("select version from daily_log where id = " + zgDraftLog),
				id("select id from site where code = 'ZG-02'"),
				id("select id from app_user where email = '" + MANAGER_ZG + "'"),
				id("select id from app_user where email = '" + INSPECTOR + "'"),
				id("select min(id) from crew where active"), seedIncidents(), seedReopens());
	}

	private Incidents seedIncidents() {
		long stHigh = id("select id from incident where title = 'Scaffold tube dropped from level 4'");
		jdbc.update("update incident set root_cause = 'Toe board missing', medical_treatment = 'None', "
				+ "authority_notified = false where id = ?", stHigh);
		long zgLow = id("select id from incident where title = 'Worker slipped on wet stairs'");
		long zgSigned = id("select id from incident where title = 'Fall from slab edge'");
		long backStrain = id("select id from incident where title = 'Back strain while unloading blocks'");
		return new Incidents(zgLow, id("select version from incident where id = " + zgLow), stHigh,
				id("select version from incident where id = " + stHigh), zgSigned,
				id("select version from incident where id = " + zgSigned), backStrain,
				id("select version from incident where id = " + backStrain),
				id("select id from corrective_action where incident_id = " + backStrain + " and status = 'OPEN'"));
	}

	private Reopens seedReopens() {
		long zgSignedLog = id("select dl.id from daily_log dl join site s on s.id = dl.site_id "
				+ "where s.code = 'ZG-01' and dl.log_date = date '2026-10-26'");
		long stPendingRequest = id("select id from log_reopen_request where status = 'PENDING'");
		return new Reopens(zgSignedLog, id("select version from daily_log where id = " + zgSignedLog),
				id("select daily_log_id from log_reopen_request where id = " + stPendingRequest), stPendingRequest);
	}

	/** The endpoints of the matrix, each with the users that may call it. */
	enum Endpoint {

		// ---------------------------------------------------------------- pages (GET)
		SITE_LIST(GET, EVERYONE, ids -> get("/sites")),
		SITE_DETAIL(GET, EVERYONE, ids -> get("/sites/{id}", ids.zg01())),
		SITE_NEW_FORM(GET, Set.of(ADMIN), ids -> get("/sites/new")),
		SITE_EDIT_FORM(GET, Set.of(ADMIN), ids -> get("/sites/{id}/edit", ids.zg01())),
		CREW_LIST(GET, EVERYONE, ids -> get("/crews")),
		CREW_NEW_FORM(GET, Set.of(ADMIN), ids -> get("/crews/new")),
		CREW_EDIT_FORM(GET, Set.of(ADMIN), ids -> get("/crews/{id}/edit", ids.crew())),
		DAILY_LOG_NEW_FORM(GET, Set.of(MANAGER_ZG), ids -> get("/sites/{id}/daily-logs/new", ids.zg01())),
		DAILY_LOG_DETAIL(GET, EVERYONE, ids -> get("/daily-logs/{id}", ids.zgDraftLog())),
		DAILY_LOG_EDIT_FORM(GET, Set.of(MANAGER_ZG), ids -> get("/daily-logs/{id}/edit", ids.zgDraftLog())),
		WORK_ENTRY_NEW_FORM(GET, Set.of(MANAGER_ZG), ids -> get("/daily-logs/{id}/work-entries/new", ids.zgDraftLog())),
		ADMIN_USER_LIST(GET, Set.of(ADMIN), ids -> get("/admin/users")),
		NOTIFICATION_LIST(GET, EVERYONE, ids -> get("/notifications")),
		PROFILE(GET, EVERYONE, ids -> get("/profile")),
		INCIDENT_LIST(GET, EVERYONE, ids -> get("/incidents")),
		SITE_INCIDENT_LIST(GET, EVERYONE, ids -> get("/sites/{id}/incidents", ids.zg01())),
		INCIDENT_DETAIL(GET, EVERYONE, ids -> get("/incidents/{id}", ids.incidents().stHigh())),
		INCIDENT_NEW_FORM(GET, Set.of(MANAGER_ZG), ids -> get("/sites/{id}/incidents/new", ids.zg01())),
		INCIDENT_EDIT_FORM(GET, Set.of(MANAGER_ZG, SUPERVISOR), ids -> get("/incidents/{id}/edit", ids.incidents().zgLow())),
		CORRECTIVE_ACTION_NEW_FORM(GET, Set.of(SUPERVISOR),
				ids -> get("/incidents/{id}/corrective-actions/new", ids.incidents().zgSigned())),
		/** The queue is the supervisor's work list only (decided 24/11). */
		REOPEN_QUEUE(GET, Set.of(SUPERVISOR), ids -> get("/reopen-requests")),
		DAILY_LOG_HISTORY(GET, EVERYONE, ids -> get("/daily-logs/{id}/history", ids.reopens().zgSignedLog())),

		// ---------------------------------------------------------------- actions (POST + CSRF)
		SITE_CREATE(POST, Set.of(ADMIN), ids -> post("/sites")
			.param("code", "MX-01").param("name", "Matrix site").param("address", "Ilica 1, Zagreb")
			.param("startDate", "2026-11-02").param("managerId", String.valueOf(ids.managerZg()))),
		/** A site with logs cannot be deleted: the Admin gets a redirect with an error flash, nothing changes. */
		SITE_DELETE(POST, Set.of(ADMIN), ids -> post("/sites/{id}/delete", ids.zg02())),
		CREW_CREATE(POST, Set.of(ADMIN), ids -> post("/crews")
			.param("name", "Matrix crew").param("company", "Matrix d.o.o.").param("trade", "Painting")
			.param("active", "true")),
		DAILY_LOG_CREATE_ZAGREB(POST, Set.of(MANAGER_ZG), ids -> dailyLogForm(post("/sites/{id}/daily-logs", ids.zg01()), ids)),
		DAILY_LOG_CREATE_SPLIT(POST, Set.of(MANAGER_ST), ids -> dailyLogForm(post("/sites/{id}/daily-logs", ids.st01()), ids)),
		DAILY_LOG_SIGN_OFF(POST, Set.of(MANAGER_ZG), ids -> post("/daily-logs/{id}/sign-off", ids.zgDraftLog())
			.param("version", String.valueOf(ids.zgDraftLogVersion()))),
		WORK_ENTRY_CREATE(POST, Set.of(MANAGER_ZG), ids -> post("/daily-logs/{id}/work-entries", ids.zgDraftLog())
			.param("logVersion", String.valueOf(ids.zgDraftLogVersion()))
			.param("description", "Matrix work entry").param("startTime", "07:00").param("endTime", "09:00")),
		USER_DEACTIVATE(POST, Set.of(ADMIN), ids -> post("/admin/users/{id}/deactivate", ids.inspector())),
		NOTIFICATIONS_READ_ALL(POST, EVERYONE, ids -> post("/notifications/read-all")),
		INCIDENT_REPORT(POST, Set.of(MANAGER_ZG), ids -> post("/sites/{id}/incidents", ids.zg01())
			.param("title", "Matrix incident").param("description", "Matrix").param("occurredAt", "2026-11-02T09:00")
			.param("severity", "LOW").param("category", "NEAR_MISS")),
		INCIDENT_START_REVIEW(POST, Set.of(MANAGER_ZG, SUPERVISOR), ids -> post("/incidents/{id}/start-review",
				ids.incidents().zgLow()).param("version", String.valueOf(ids.incidents().zgLowVersion()))),
		/** AR2: a HIGH incident is signed off only by the safety supervisor - even its own site's manager gets 403. */
		INCIDENT_SIGN_OFF_HIGH(POST, Set.of(SUPERVISOR), ids -> post("/incidents/{id}/sign-off",
				ids.incidents().stHigh()).param("version", String.valueOf(ids.incidents().stHighVersion()))),
		CORRECTIVE_ACTION_CREATE(POST, Set.of(SUPERVISOR), ids -> post("/incidents/{id}/corrective-actions",
				ids.incidents().zgSigned()).param("description", "Matrix action").param("dueDate", "2027-01-15")
			.param("incidentVersion", String.valueOf(ids.incidents().zgSignedVersion()))),
		/** Mark as done: the supervisor, or the action's assignee (manager@). */
		CORRECTIVE_ACTION_DONE(POST, Set.of(SUPERVISOR, MANAGER_ZG), ids -> post(
				"/incidents/{id}/corrective-actions/{a}/done", ids.incidents().backStrain(),
				ids.incidents().backStrainAction()).param("version", String.valueOf(ids.incidents().backStrainVersion()))),
		REOPEN_REQUEST(POST, Set.of(MANAGER_ZG), ids -> post("/daily-logs/{id}/reopen-requests", ids.reopens().zgSignedLog())
			.param("reason", "Matrix reason").param("version", String.valueOf(ids.reopens().zgSignedLogVersion()))),
		REOPEN_APPROVE(POST, Set.of(SUPERVISOR), ids -> post("/daily-logs/{id}/reopen-requests/{r}/approve",
				ids.reopens().stPendingLog(), ids.reopens().stPendingRequest())),
		REOPEN_REJECT(POST, Set.of(SUPERVISOR), ids -> post("/daily-logs/{id}/reopen-requests/{r}/reject",
				ids.reopens().stPendingLog(), ids.reopens().stPendingRequest()).param("reason", "Matrix rejection"));

		final HttpMethod method;

		final Set<String> allowed;

		final Function<Ids, MockHttpServletRequestBuilder> request;

		Endpoint(HttpMethod method, Set<String> allowed, Function<Ids, MockHttpServletRequestBuilder> request) {
			this.method = method;
			this.allowed = allowed;
			this.request = request;
		}

		boolean isPost() {
			return method == POST;
		}

		MockHttpServletRequestBuilder build(Ids ids) {
			MockHttpServletRequestBuilder builder = request.apply(ids);
			return isPost() ? builder.with(csrf()) : builder;
		}

	}

	/** A complete, valid daily log form (one work entry with one crew row) for a past date. */
	private static MockHttpServletRequestBuilder dailyLogForm(MockHttpServletRequestBuilder request, Ids ids) {
		return request.param("logDate", "2026-11-02").param("weather", "SUNNY")
			.param("workEntries[0].description", "Matrix work").param("workEntries[0].startTime", "07:00")
			.param("workEntries[0].endTime", "15:00")
			.param("workEntries[0].crewAssignments[0].crewId", String.valueOf(ids.crew()))
			.param("workEntries[0].crewAssignments[0].hoursWorked", "8")
			.param("workEntries[0].crewAssignments[0].workerCount", "3");
	}

	static Stream<Arguments> matrix() {
		return Arrays.stream(Endpoint.values())
			.flatMap(endpoint -> EVERYONE.stream().sorted()
				.map(email -> Arguments.of(endpoint, email, endpoint.allowed.contains(email))));
	}

	@ParameterizedTest(name = "{0} as {1} -> allowed={2}")
	@MethodSource("matrix")
	void endpointAnswersAccordingToTheRoleMatrix(Endpoint endpoint, String email, boolean allowed) throws Exception {
		MvcResult result = mvc.perform(endpoint.build(ids).with(user(users.loadUserByUsername(email)))).andReturn();
		int status = result.getResponse().getStatus();

		if (!allowed) {
			assertThat(status).as("%s as %s must be refused", endpoint, email)
				.isEqualTo(HttpStatus.FORBIDDEN.value());
		}
		else if (endpoint.isPost()) {
			assertThat(status).as("%s as %s must redirect (PRG)", endpoint, email)
				.isEqualTo(HttpStatus.FOUND.value());
			assertThat(result.getResponse().getRedirectedUrl()).as("redirect target").isNotBlank();
		}
		else {
			assertThat(status).as("%s as %s must render the page", endpoint, email).isEqualTo(HttpStatus.OK.value());
			assertThat(result.getModelAndView()).as("a view was rendered").isNotNull();
		}
	}

	/**
	 * POSTs that re-render the form instead of redirecting: a row action of the new daily log form
	 * ("Add work entry", nothing saved) and a crew form with validation errors. Both show the form,
	 * so they follow the same rule as the form itself: only its owner gets the page (200).
	 */
	enum FormPost {

		DAILY_LOG_ROW_ACTION(Set.of(MANAGER_ZG), ids -> dailyLogForm(post("/sites/{id}/daily-logs", ids.zg01()), ids)
			.param("action", "addEntry")),
		DAILY_LOG_ROW_ACTION_EDIT(Set.of(MANAGER_ZG), ids -> dailyLogForm(post("/daily-logs/{id}", ids.zgDraftLog()), ids)
			.param("version", String.valueOf(ids.zgDraftLogVersion())).param("action", "addEntry")),
		CREW_CREATE_INVALID(Set.of(ADMIN), ids -> post("/crews").param("name", "").param("company", "")
			.param("trade", ""));

		final Set<String> allowed;

		final Function<Ids, MockHttpServletRequestBuilder> request;

		FormPost(Set<String> allowed, Function<Ids, MockHttpServletRequestBuilder> request) {
			this.allowed = allowed;
			this.request = request;
		}

	}

	static Stream<Arguments> formPosts() {
		return Arrays.stream(FormPost.values())
			.flatMap(post -> EVERYONE.stream().sorted()
				.map(email -> Arguments.of(post, email, post.allowed.contains(email))));
	}

	@ParameterizedTest(name = "{0} as {1} -> allowed={2}")
	@MethodSource("formPosts")
	void formRerenderingPostsAnswerAccordingToTheRoleMatrix(FormPost formPost, String email, boolean allowed)
			throws Exception {
		MvcResult result = mvc.perform(formPost.request.apply(ids).with(csrf())
				.with(user(users.loadUserByUsername(email)))).andReturn();

		assertThat(result.getResponse().getStatus()).as("%s as %s", formPost, email)
			.isEqualTo(allowed ? HttpStatus.OK.value() : HttpStatus.FORBIDDEN.value());
	}

	@ParameterizedTest(name = "anonymous {0} -> login page")
	@EnumSource(Endpoint.class)
	void anonymousCallerIsSentToTheLoginPage(Endpoint endpoint) throws Exception {
		MvcResult result = mvc.perform(endpoint.build(ids)).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FOUND.value());
		assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login");
	}

	private long id(String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}

}
