package hr.algebra.sitelog.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import hr.algebra.sitelog.TestcontainersConfiguration;

/**
 * Forbidden calls end to end, over real HTTP: embedded Tomcat on a random port, the real
 * security filter chain, {@code @PreAuthorize} on the services, Flyway + the V4 seed users on a
 * Testcontainers Postgres. Unlike MockMvc, Tomcat performs the error dispatch, so a refused call
 * shows the real {@code error/403} page in the user's language.
 *
 * <p>The client behaves like a browser: it keeps the session cookie, signs in through the login
 * form and copies the hidden {@code _csrf} field from the last page into every POST - so a 403
 * here is the authorization rule, not a missing token (except in the test that leaves the token
 * out on purpose). Every forbidden POST is also checked in the database: nothing changed.
 *
 * <p>Not {@code @Transactional} (the server runs requests on its own threads): the tests only
 * read or attempt forbidden writes; the one row a test inserts is removed after it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ForbiddenCallsHttpTests {

	static final String DEMO_PASSWORD = "SiteLog-demo-2026";

	static final String INACTIVE_USER = "inactive.http@sitelog.test";

	/** Title of error/403 in messages.properties (en) and messages_hr.properties (hr). */
	static final String ACCESS_DENIED_EN = "Access denied";

	static final String ACCESS_DENIED_HR = "Pristup odbijen";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@AfterEach
	void removeInactiveUser() {
		jdbc.update("delete from app_user where email = ?", INACTIVE_USER);
	}

	// ------------------------------------------------------------------ the 403s

	@Test
	void splitManagerCreatingALogOnAZagrebSite_isForbiddenAndNothingIsSaved() throws Exception {
		Browser split = signedIn("manager.split@sitelog.test");
		long zg01 = siteId("ZG-01");
		int logsBefore = count("select count(*) from daily_log where site_id = " + zg01);

		HttpResponse<String> response = split.post("/sites/" + zg01 + "/daily-logs", validDailyLog());

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(response.body()).contains(ACCESS_DENIED_HR).doesNotContain("Exception");
		assertThat(count("select count(*) from daily_log where site_id = " + zg01)).isEqualTo(logsBefore);
	}

	@Test
	void splitManagerSigningOffAZagrebLog_isForbiddenAndTheLogStaysDraft() throws Exception {
		Browser split = signedIn("manager.split@sitelog.test");
		long log = zagrebDraftLog();
		long version = count("select version from daily_log where id = " + log);

		HttpResponse<String> response = split.post("/daily-logs/" + log + "/sign-off",
				Map.of("version", String.valueOf(version)));

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(response.body()).contains(ACCESS_DENIED_HR);
		assertThat(jdbc.queryForObject("select status from daily_log where id = ?", String.class, log))
			.isEqualTo("DRAFT");
		assertThat(count("select version from daily_log where id = " + log)).isEqualTo(version);
	}

	@Test
	void ownManagerMayOpenTheSameLogForEditing_controlCase() throws Exception {
		Browser manager = signedIn("manager@sitelog.test");

		HttpResponse<String> response = manager.get("/daily-logs/" + zagrebDraftLog() + "/edit");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).doesNotContain(ACCESS_DENIED_HR);
	}

	@Test
	void inspectorCreatingALog_isForbiddenWithTheEnglishErrorPage() throws Exception {
		Browser inspector = signedIn("inspector@sitelog.test");
		long st01 = siteId("ST-01");
		int logsBefore = count("select count(*) from daily_log");

		HttpResponse<String> response = inspector.post("/sites/" + st01 + "/daily-logs", validDailyLog());

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(response.body()).contains(ACCESS_DENIED_EN, "Error 403");
		assertThat(count("select count(*) from daily_log")).isEqualTo(logsBefore);
	}

	@Test
	void safetySupervisorCreatingASite_isForbiddenAndNoSiteIsAdded() throws Exception {
		Browser supervisor = signedIn("supervisor@sitelog.test");
		int sitesBefore = count("select count(*) from site");

		HttpResponse<String> response = supervisor.post("/sites", Map.of("code", "HX-01", "name", "Hacked site",
				"address", "Ilica 1, Zagreb", "startDate", "2026-11-02",
				"managerId", String.valueOf(userId("manager@sitelog.test"))));

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(response.body()).contains(ACCESS_DENIED_EN);
		assertThat(count("select count(*) from site")).isEqualTo(sitesBefore);
		assertThat(count("select count(*) from site where code = 'HX-01'")).isZero();
	}

	@Test
	void nonAdminsOpeningTheUserAdministration_areForbidden() throws Exception {
		for (String email : new String[] { "manager@sitelog.test", "supervisor@sitelog.test",
				"inspector@sitelog.test" }) {
			HttpResponse<String> response = signedIn(email).get("/admin/users");

			assertThat(response.statusCode()).as(email).isEqualTo(403);
			assertThat(response.body()).as(email).containsAnyOf(ACCESS_DENIED_EN, ACCESS_DENIED_HR);
		}
		assertThat(signedIn("admin@sitelog.test").get("/admin/users").statusCode()).isEqualTo(200);
	}

	@Test
	void supervisorDeactivatingAUser_isForbiddenAndTheUserStaysActive() throws Exception {
		Browser supervisor = signedIn("supervisor@sitelog.test");
		long inspector = userId("inspector@sitelog.test");

		HttpResponse<String> response = supervisor.post("/admin/users/" + inspector + "/deactivate", Map.of());

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(jdbc.queryForObject("select active from app_user where id = ?", Boolean.class, inspector)).isTrue();
	}

	// ------------------------------------------------------------------ CSRF

	@Test
	void postWithoutTheCsrfToken_isForbiddenEvenForASignedInUser() throws Exception {
		Browser inspector = signedIn("inspector@sitelog.test");

		HttpResponse<String> withoutToken = inspector.postWithoutCsrf("/notifications/read-all", Map.of());
		HttpResponse<String> withToken = inspector.post("/notifications/read-all", Map.of());

		assertThat(withoutToken.statusCode()).isEqualTo(403);
		assertThat(withoutToken.body()).contains(ACCESS_DENIED_EN);
		assertThat(withToken.statusCode()).as("same call with the token is allowed").isEqualTo(302);
	}

	@Test
	void postWithAForgedCsrfToken_isForbidden() throws Exception {
		Browser manager = signedIn("manager@sitelog.test");
		manager.csrf = "forged-token";

		HttpResponse<String> response = manager.post("/sites/" + siteId("ZG-01") + "/daily-logs", validDailyLog());

		assertThat(response.statusCode()).isEqualTo(403);
	}

	// ------------------------------------------------------------------ anonymous

	@Test
	void anonymousPageRequest_isRedirectedToTheLoginPage() throws Exception {
		Browser anonymous = new Browser();

		for (String path : new String[] { "/sites", "/admin/users", "/daily-logs/" + zagrebDraftLog() + "/edit" }) {
			HttpResponse<String> response = anonymous.get(path);

			assertThat(response.statusCode()).as(path).isEqualTo(302);
			assertThat(location(response)).as(path).endsWith("/login");
		}
	}

	@Test
	void anonymousPostWithAValidCsrfToken_isRedirectedToTheLoginPageAndNothingIsSaved() throws Exception {
		Browser anonymous = new Browser();
		anonymous.get("/login"); // the login page hands an anonymous session a token
		int logsBefore = count("select count(*) from daily_log");

		HttpResponse<String> response = anonymous.post("/sites/" + siteId("ZG-01") + "/daily-logs", validDailyLog());

		assertThat(response.statusCode()).isEqualTo(302);
		assertThat(location(response)).endsWith("/login");
		assertThat(count("select count(*) from daily_log")).isEqualTo(logsBefore);
	}

	// ------------------------------------------------------------------ login / logout

	@Test
	void inactiveUserCannotSignIn() throws Exception {
		jdbc.update("""
				insert into app_user (email, password_hash, full_name, preferred_language, active, role_id)
				select ?, password_hash, 'Inactive Tester', 'en', false, role_id from app_user where email = ?""",
				INACTIVE_USER, "inspector@sitelog.test");
		Browser browser = new Browser();

		HttpResponse<String> login = browser.login(INACTIVE_USER, DEMO_PASSWORD);

		assertThat(login.statusCode()).isEqualTo(302);
		assertThat(location(login)).endsWith("/login?error");
		assertThat(location(browser.get("/sites"))).as("still anonymous").endsWith("/login");
	}

	@Test
	void logoutInvalidatesTheServerSession() throws Exception {
		Browser supervisor = signedIn("supervisor@sitelog.test");
		String oldSession = supervisor.cookies.get("JSESSIONID");
		assertThat(supervisor.get("/sites").statusCode()).as("signed in before logout").isEqualTo(200);

		HttpResponse<String> logout = supervisor.post("/logout", Map.of());

		assertThat(logout.statusCode()).isEqualTo(302);
		assertThat(location(logout)).endsWith("/login?logout");
		// replay the OLD session cookie, as an attacker who copied it would: the server forgot it
		Browser replay = new Browser();
		replay.cookies.put("JSESSIONID", oldSession);
		HttpResponse<String> afterLogout = replay.get("/sites");
		assertThat(afterLogout.statusCode()).isEqualTo(302);
		assertThat(location(afterLogout)).endsWith("/login");
	}

	@Test
	void loginChangesTheSessionId() throws Exception {
		Browser browser = new Browser();
		browser.get("/login");
		String anonymousSession = browser.cookies.get("JSESSIONID");

		browser.login("inspector@sitelog.test", DEMO_PASSWORD);

		assertThat(browser.cookies.get("JSESSIONID")).as("session fixation protection")
			.isNotNull().isNotEqualTo(anonymousSession);
	}

	// ------------------------------------------------------------------ helpers

	private Browser signedIn(String email) throws Exception {
		Browser browser = new Browser();
		HttpResponse<String> login = browser.login(email, DEMO_PASSWORD);
		assertThat(location(login)).as("login of " + email).endsWith("/sites");
		browser.get("/sites"); // a page with the navbar logout form = a fresh CSRF token for the new session
		return browser;
	}

	private Map<String, String> validDailyLog() {
		Map<String, String> form = new LinkedHashMap<>();
		form.put("logDate", "2026-11-02");
		form.put("weather", "SUNNY");
		form.put("workEntries[0].description", "Forbidden work");
		form.put("workEntries[0].startTime", "07:00");
		form.put("workEntries[0].endTime", "15:00");
		form.put("workEntries[0].crewAssignments[0].crewId",
				String.valueOf(count("select min(id) from crew where active")));
		form.put("workEntries[0].crewAssignments[0].hoursWorked", "8");
		form.put("workEntries[0].crewAssignments[0].workerCount", "3");
		return form;
	}

	private long zagrebDraftLog() {
		return count("""
				select dl.id from daily_log dl join site s on s.id = dl.site_id
				where s.code = 'ZG-01' and dl.log_date = date '2026-10-29'""");
	}

	private long siteId(String code) {
		return jdbc.queryForObject("select id from site where code = ?", Long.class, code);
	}

	private long userId(String email) {
		return jdbc.queryForObject("select id from app_user where email = ?", Long.class, email);
	}

	private int count(String sql) {
		return jdbc.queryForObject(sql, Integer.class);
	}

	private static String location(HttpResponse<String> response) {
		return response.headers().firstValue("Location").orElse("");
	}

	/**
	 * A tiny browser: asks for HTML, one cookie jar (Set-Cookie in, Cookie out), no automatic
	 * redirects, and the last {@code _csrf} hidden field seen on a page.
	 */
	final class Browser {

		private static final Pattern CSRF_FIELD = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");

		private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

		final Map<String, String> cookies = new LinkedHashMap<>();

		String csrf;

		HttpResponse<String> login(String email, String password) throws IOException, InterruptedException {
			HttpResponse<String> page = get("/login");
			assertThat(page.statusCode()).as("login page").isEqualTo(200);
			return post("/login", Map.of("email", email, "password", password));
		}

		HttpResponse<String> get(String path) throws IOException, InterruptedException {
			return send(request(path).GET());
		}

		HttpResponse<String> post(String path, Map<String, String> form) throws IOException, InterruptedException {
			Map<String, String> withToken = new LinkedHashMap<>(form);
			withToken.put("_csrf", csrf);
			return postWithoutCsrf(path, withToken);
		}

		HttpResponse<String> postWithoutCsrf(String path, Map<String, String> form)
				throws IOException, InterruptedException {
			String body = form.entrySet().stream()
				.map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
				.collect(Collectors.joining("&"));
			return send(request(path).header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body)));
		}

		private HttpRequest.Builder request(String path) {
			// a browser asks for HTML; without it Boot's /error answers with JSON instead of error/403
			HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.header("Accept", "text/html,application/xhtml+xml");
			if (!cookies.isEmpty()) {
				builder.header("Cookie", cookies.entrySet().stream()
					.map(e -> e.getKey() + "=" + e.getValue())
					.collect(Collectors.joining("; ")));
			}
			return builder;
		}

		private HttpResponse<String> send(HttpRequest.Builder builder) throws IOException, InterruptedException {
			HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
			for (String header : response.headers().allValues("Set-Cookie")) {
				String[] nameValue = header.split(";", 2)[0].split("=", 2);
				boolean expired = header.toLowerCase().contains("max-age=0");
				if (expired || nameValue[1].isEmpty()) {
					cookies.remove(nameValue[0]);
				}
				else {
					cookies.put(nameValue[0], nameValue[1]);
				}
			}
			Matcher token = CSRF_FIELD.matcher(response.body());
			if (token.find()) {
				csrf = token.group(1);
			}
			return response;
		}

		private static String encode(String value) {
			return URLEncoder.encode(value, StandardCharsets.UTF_8);
		}

	}

}
