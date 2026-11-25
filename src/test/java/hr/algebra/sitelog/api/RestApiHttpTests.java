package hr.algebra.sitelog.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * The REST API end to end over real HTTP (embedded Tomcat, real filter chain, method security,
 * Flyway seed on Testcontainers Postgres), following the client flow documented in the OpenAPI
 * description: {@code GET /api/v1/csrf} -> form {@code POST /login} -> {@code GET /api/v1/csrf}
 * again -> JSON calls with the session cookie and the {@code X-CSRF-TOKEN} header.
 *
 * <p>Not {@code @Transactional} (the server runs on its own threads): what a test creates it
 * deletes through the API, and {@link #cleanUp()} removes leftovers of a failed run.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RestApiHttpTests {

	static final String DEMO_PASSWORD = "SiteLog-demo-2026";

	static final String TEST_SITE = "API-T1";

	/** Far in the past: no seed log uses it and it is never "in the future". */
	static final String TEST_DATE = "2001-01-15";

	static final String TEST_INCIDENT = "API test incident";

	private static final Pattern VERSION = Pattern.compile("\"version\":(\\d+)");

	private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@AfterEach
	void cleanUp() {
		String incidents = "select id from incident where title like '" + TEST_INCIDENT + "%'";
		jdbc.update("delete from notification where link in (select '/incidents/' || id from incident where title like ?)",
				TEST_INCIDENT + "%");
		jdbc.update("delete from corrective_action where incident_id in (" + incidents + ")");
		jdbc.update("delete from incident_affected_crew where incident_id in (" + incidents + ")");
		jdbc.update("delete from incident where title like ?", TEST_INCIDENT + "%");
		String logs = "select id from daily_log where log_date = '" + TEST_DATE + "'::date";
		jdbc.update("delete from crew_assignment where work_entry_id in (select id from work_entry where daily_log_id in ("
				+ logs + "))");
		jdbc.update("delete from work_entry where daily_log_id in (" + logs + ")");
		jdbc.update("delete from daily_log where log_date = ?::date", TEST_DATE);
		jdbc.update("delete from site where code = ?", TEST_SITE);
	}

	@Test
	void csrfEndpointIsPublicAndHandsOutTheHeaderName() throws Exception {
		HttpResponse<String> response = new Client().get("/api/v1/csrf");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"headerName\":\"X-CSRF-TOKEN\"", "\"token\"");
		assertThat(response.headers().firstValue("Cache-Control")).hasValueSatisfying(v -> assertThat(v).contains("no-store"));
	}

	@Test
	void anonymousApiCall_is401ProblemDetailNotTheLoginPage() throws Exception {
		HttpResponse<String> response = new Client().get("/api/v1/sites");

		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
				type -> assertThat(type).startsWith("application/problem+json"));
		assertThat(response.body()).contains("\"status\":401", "\"instance\":\"/api/v1/sites\"");
	}

	@Test
	void anonymousScreenRequest_stillRedirectsToTheLoginPage() throws Exception {
		HttpResponse<String> response = new Client().get("/sites");

		assertThat(response.statusCode()).isEqualTo(302);
		assertThat(response.headers().firstValue("Location")).hasValueSatisfying(l -> assertThat(l).endsWith("/login"));
	}

	@Test
	void adminCreatesReadsAndDeletesASite() throws Exception {
		Client admin = signedIn("admin@sitelog.test");
		long managerId = jdbc.queryForObject("select id from app_user where email = 'manager@sitelog.test'", Long.class);

		HttpResponse<String> created = admin.send("POST", "/api/v1/sites", """
				{"code":"%s","name":"API test site","address":"Ilica 1, Zagreb","startDate":"2001-01-01","managerId":%d}"""
			.formatted(TEST_SITE, managerId));

		assertThat(created.statusCode()).isEqualTo(201);
		String location = created.headers().firstValue("Location").orElseThrow();
		assertThat(location).matches("http://localhost:" + port + "/api/v1/sites/\\d+");
		String path = URI.create(location).getPath();
		assertThat(admin.get(path).body()).contains("\"code\":\"" + TEST_SITE + "\"");

		HttpResponse<String> duplicate = admin.send("POST", "/api/v1/sites", """
				{"code":"%s","name":"Again","address":"Ilica 1","startDate":"2001-01-01","managerId":%d}"""
			.formatted(TEST_SITE, managerId));
		assertThat(duplicate.statusCode()).isEqualTo(422);
		assertThat(duplicate.body()).contains("\"code\":\"validation.site.code.duplicate\"");

		assertThat(admin.send("DELETE", path, null).statusCode()).isEqualTo(204);
		assertThat(admin.get(path).statusCode()).isEqualTo(404);
	}

	@Test
	void siteManagerCreatingASite_is403ProblemAndNothingIsSaved() throws Exception {
		Client manager = signedIn("manager@sitelog.test");

		HttpResponse<String> response = manager.send("POST", "/api/v1/sites", """
				{"code":"%s","name":"Nope","address":"Ilica 1","startDate":"2001-01-01","managerId":1}"""
			.formatted(TEST_SITE));

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(response.body()).contains("\"status\":403").doesNotContain("<html");
		assertThat(jdbc.queryForObject("select count(*) from site where code = ?", Integer.class, TEST_SITE)).isZero();
	}

	@Test
	void writeWithoutTheCsrfHeader_is403EvenWhenSignedIn() throws Exception {
		Client admin = signedIn("admin@sitelog.test");
		admin.csrf = null;

		HttpResponse<String> response = admin.send("DELETE", "/api/v1/sites/999999", null);

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(response.body()).contains("\"code\":\"error.csrf\"");
	}

	@Test
	void managerWritesADailyLogOfOwnSite_staleVersionIs409() throws Exception {
		Client manager = signedIn("manager@sitelog.test");
		long siteId = jdbc.queryForObject("""
				select s.id from site s join app_user u on u.id = s.manager_id
				where u.email = 'manager@sitelog.test' order by s.id limit 1""", Long.class);
		long crewId = jdbc.queryForObject("select id from crew where active order by id limit 1", Long.class);

		HttpResponse<String> created = manager.send("POST", "/api/v1/sites/" + siteId + "/daily-logs", """
				{"logDate":"%s","weather":"SUNNY","workEntries":[{"description":"API entry",
				  "crewAssignments":[{"crewId":%d,"hoursWorked":8,"workerCount":3}]}]}""".formatted(TEST_DATE, crewId));

		assertThat(created.statusCode()).isEqualTo(201);
		assertThat(created.body()).contains("\"status\":\"DRAFT\"", "\"version\":0");
		String path = URI.create(created.headers().firstValue("Location").orElseThrow()).getPath();

		String update = """
				{"version":%d,"logDate":"%s","weather":"RAIN","workEntries":[{"description":"API entry, changed"}]}""";
		HttpResponse<String> updated = manager.send("PUT", path, update.formatted(0, TEST_DATE));
		assertThat(updated.statusCode()).isEqualTo(200);
		Matcher version = Pattern.compile("\"version\":(\\d+)").matcher(updated.body());
		assertThat(version.find()).isTrue();
		assertThat(Long.parseLong(version.group(1))).isPositive();
		HttpResponse<String> stale = manager.send("PUT", path, update.formatted(0, TEST_DATE));
		assertThat(stale.statusCode()).isEqualTo(409);
		assertThat(stale.body()).contains("\"code\":\"dailyLog.flash.conflict\"");

		HttpResponse<String> deleteStale = manager.send("DELETE", path + "?version=0", null);
		assertThat(deleteStale.statusCode()).isEqualTo(409);
		assertThat(manager.send("DELETE", path + "?version=" + version.group(1), null).statusCode()).isEqualTo(204);
		assertThat(manager.get(path).statusCode()).isEqualTo(404);
	}

	@Test
	void openApiDocumentIsServedToSignedInUsersOnly() throws Exception {
		assertThat(new Client().get("/v3/api-docs").statusCode()).isEqualTo(302);

		HttpResponse<String> docs = signedIn("inspector@sitelog.test").get("/v3/api-docs");

		assertThat(docs.statusCode()).isEqualTo(200);
		assertThat(docs.body()).contains("\"/api/v1/sites\"", "\"/api/v1/sites/{siteId}/daily-logs\"",
				"\"/api/v1/daily-logs/{id}/sign-off\"",
				"\"/api/v1/daily-logs/{logId}/work-entries/{entryId}/crew-assignments/{id}\"",
				"\"/api/v1/sites/{siteId}/incidents\"", "\"/api/v1/incidents/{id}/sign-off\"",
				"\"/api/v1/incidents/{incidentId}/corrective-actions/{id}/done\"",
				"\"/api/v1/daily-logs/{id}/reopen-requests/{requestId}/approve\"", "\"/api/v1/daily-logs/{id}/revisions\"",
				"\"X-CSRF-TOKEN\"",
				"application/problem+json");
		assertThat(docs.body()).doesNotContain("\"/sites\"", "\"/login\"");
		assertThat(signedIn("inspector@sitelog.test").get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
	}

	/**
	 * AR2 end to end: required fields by severity (422 listing them), the manager cannot sign off
	 * a HIGH incident (403 ProblemDetail from method security), the supervisor can, and an
	 * incident with an open corrective action cannot be closed (409).
	 */
	@Test
	void highSeverityIncidentWorkflow() throws Exception {
		Client manager = signedIn("manager@sitelog.test");
		Client supervisor = signedIn("supervisor@sitelog.test");
		long siteId = jdbc.queryForObject("""
				select s.id from site s join app_user u on u.id = s.manager_id
				where u.email = 'manager@sitelog.test' order by s.id limit 1""", Long.class);
		String occurredAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).truncatedTo(ChronoUnit.MINUTES).toString();
		String report = """
				{"occurredAt":"%s","title":"%s","description":"Worker slipped on the wet deck",
				 "severity":"HIGH","category":"SLIP_TRIP"%s}""";

		HttpResponse<String> missing = manager.send("POST", "/api/v1/sites/" + siteId + "/incidents",
				report.formatted(occurredAt, TEST_INCIDENT, ""));
		assertThat(missing.statusCode()).isEqualTo(422);
		assertThat(missing.headers().firstValue("Content-Type")).hasValueSatisfying(
				type -> assertThat(type).startsWith("application/problem+json"));
		assertThat(missing.body()).contains("\"code\":\"incident.flash.missingFields\"", "\"field\":\"rootCause\"",
				"\"field\":\"witnesses\"", "\"field\":\"medicalTreatment\"", "\"field\":\"authorityNotified\"");
		assertThat(jdbc.queryForObject("select count(*) from incident where title = ?", Integer.class, TEST_INCIDENT))
			.isZero();

		HttpResponse<String> created = manager.send("POST", "/api/v1/sites/" + siteId + "/incidents",
				report.formatted(occurredAt, TEST_INCIDENT, """
						,"rootCause":"No anti-slip mats","witnesses":"None","medicalTreatment":"First aid",
						 "authorityNotified":false"""));
		assertThat(created.statusCode()).isEqualTo(201);
		assertThat(created.body()).contains("\"status\":\"REPORTED\"", "\"requiresSupervisorSignOff\":true");
		String path = URI.create(created.headers().firstValue("Location").orElseThrow()).getPath();
		assertThat(path).matches("/api/v1/incidents/\\d+");

		HttpResponse<String> review = supervisor.send("POST", path + "/start-review", versionBody(created));
		assertThat(review.statusCode()).isEqualTo(200);
		assertThat(review.body()).contains("\"status\":\"UNDER_REVIEW\"");

		HttpResponse<String> managerSignOff = manager.send("POST", path + "/sign-off", versionBody(review));
		assertThat(managerSignOff.statusCode()).isEqualTo(403);
		assertThat(managerSignOff.body()).contains("\"status\":403", "\"code\":\"error.403.body\"")
			.doesNotContain("<html");

		HttpResponse<String> signed = supervisor.send("POST", path + "/sign-off", versionBody(review));
		assertThat(signed.statusCode()).isEqualTo(200);
		assertThat(signed.body()).contains("\"status\":\"SIGNED_OFF\"", "\"signedOffByName\"");

		HttpResponse<String> action = supervisor.send("POST", path + "/corrective-actions", """
				{"incidentVersion":%d,"description":"Install anti-slip mats","dueDate":"%s"}"""
			.formatted(version(signed), LocalDate.now().plusDays(7)));
		assertThat(action.statusCode()).isEqualTo(201);
		String actionPath = URI.create(action.headers().firstValue("Location").orElseThrow()).getPath();
		Matcher incidentVersion = Pattern.compile("\"incidentVersion\":(\\d+)").matcher(action.body());
		assertThat(incidentVersion.find()).isTrue();
		long afterAction = Long.parseLong(incidentVersion.group(1));
		assertThat(afterAction).isGreaterThan(version(signed));

		HttpResponse<String> close = supervisor.send("POST", path + "/close", "{\"version\":" + afterAction + "}");
		assertThat(close.statusCode()).isEqualTo(409);
		assertThat(close.body()).contains("\"code\":\"incident.flash.close.openActions\"");

		HttpResponse<String> done = supervisor.send("POST", actionPath + "/done",
				"{\"incidentVersion\":" + afterAction + "}");
		assertThat(done.statusCode()).isEqualTo(200);
		assertThat(done.body()).contains("\"status\":\"DONE\"");
		HttpResponse<String> current = supervisor.get(path);
		HttpResponse<String> closed = supervisor.send("POST", path + "/close", versionBody(current));
		assertThat(closed.statusCode()).isEqualTo(200);
		assertThat(closed.body()).contains("\"status\":\"CLOSED\"");
	}

	@Test
	void reopenQueueAndRevisionHistoryAreReadable() throws Exception {
		Client inspector = signedIn("inspector@sitelog.test");
		long logId = jdbc.queryForObject("select id from daily_log order by id limit 1", Long.class);

		// the queue is for safety supervisors only; logs and their history stay readable by everyone
		assertThat(inspector.get("/api/v1/reopen-requests").statusCode()).isEqualTo(403);
		HttpResponse<String> queue = signedIn("supervisor@sitelog.test").get("/api/v1/reopen-requests");
		assertThat(queue.statusCode()).isEqualTo(200);
		assertThat(queue.body()).startsWith("[");
		assertThat(inspector.get("/api/v1/daily-logs/" + logId + "/reopen-requests").statusCode()).isEqualTo(200);
		assertThat(inspector.get("/api/v1/daily-logs/" + logId + "/revisions").statusCode()).isEqualTo(200);
		assertThat(inspector.get("/api/v1/daily-logs/999999/revisions").statusCode()).isEqualTo(404);
	}

	private static long version(HttpResponse<String> response) {
		Matcher matcher = VERSION.matcher(response.body());
		assertThat(matcher.find()).as("version in " + response.body()).isTrue();
		return Long.parseLong(matcher.group(1));
	}

	private static String versionBody(HttpResponse<String> response) {
		return "{\"version\":" + version(response) + "}";
	}

	// ---------------------------------------------------------------- client

	private Client signedIn(String email) throws Exception {
		Client client = new Client();
		client.refreshCsrf();
		HttpResponse<String> login = client.http.send(HttpRequest.newBuilder(client.uri("/login"))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString("email=" + encode(email) + "&password=" + encode(DEMO_PASSWORD)
					+ "&_csrf=" + encode(client.csrf)))
			.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(login.statusCode()).as("login of " + email).isEqualTo(302);
		assertThat(login.headers().firstValue("Location").orElse("")).doesNotContain("error");
		// the login rotates the token
		client.refreshCsrf();
		return client;
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	/** An API client: keeps the session cookie, sends the CSRF token as a header on writes. */
	private final class Client {

		final HttpClient http = HttpClient.newBuilder()
			.cookieHandler(new CookieManager())
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

		String csrf;

		URI uri(String path) {
			return URI.create("http://localhost:" + port + path);
		}

		void refreshCsrf() throws IOException, InterruptedException {
			Matcher matcher = TOKEN.matcher(get("/api/v1/csrf").body());
			assertThat(matcher.find()).isTrue();
			csrf = matcher.group(1);
		}

		HttpResponse<String> get(String path) throws IOException, InterruptedException {
			return http.send(HttpRequest.newBuilder(uri(path)).header("Accept", "application/json").GET().build(),
					HttpResponse.BodyHandlers.ofString());
		}

		HttpResponse<String> send(String method, String path, String json) throws IOException, InterruptedException {
			HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
				.header("Accept", "application/json")
				.method(method, json == null ? HttpRequest.BodyPublishers.noBody()
						: HttpRequest.BodyPublishers.ofString(json));
			if (json != null) {
				request.header("Content-Type", "application/json");
			}
			if (csrf != null) {
				request.header("X-CSRF-TOKEN", csrf);
			}
			return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
		}

	}

}
