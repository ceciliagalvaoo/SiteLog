package hr.algebra.sitelog.db;

import static hr.algebra.sitelog.db.DbFixtures.assertCheckViolation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.TestcontainersConfiguration;

/**
 * Verifies that the CHECK and UNIQUE constraints of V2/V3 reject invalid rows, independently
 * of the validation the service layer adds on top. Each test is rolled back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SchemaConstraintTests {

	private final JdbcTemplate jdbc;
	private final DbFixtures fixtures;

	@Autowired
	SchemaConstraintTests(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
		this.fixtures = new DbFixtures(jdbc);
	}

	@Test
	void hoursWorkedAbove24IsRejected() {
		long workEntry = fixtures.newWorkEntry(fixtures.newDraftLog(fixtures.newSite()));
		long crew = fixtures.newCrew();

		assertCheckViolation(() -> jdbc.update("""
				INSERT INTO crew_assignment (work_entry_id, crew_id, hours_worked, worker_count)
				VALUES (?, ?, 25, 3)""", workEntry, crew), "ck_crew_assignment_hours");
	}

	@Test
	void zeroWorkersIsRejected() {
		long workEntry = fixtures.newWorkEntry(fixtures.newDraftLog(fixtures.newSite()));
		long crew = fixtures.newCrew();

		assertCheckViolation(() -> jdbc.update("""
				INSERT INTO crew_assignment (work_entry_id, crew_id, hours_worked, worker_count)
				VALUES (?, ?, 8, 0)""", workEntry, crew), "ck_crew_assignment_workers");
	}

	@Test
	void sameCrewTwiceOnOneWorkEntryIsRejected() {
		long workEntry = fixtures.newWorkEntry(fixtures.newDraftLog(fixtures.newSite()));
		long crew = fixtures.newCrew();
		fixtures.newCrewAssignment(workEntry, crew);

		assertThatThrownBy(() -> fixtures.newCrewAssignment(workEntry, crew))
			.isInstanceOf(DuplicateKeyException.class)
			.hasMessageContaining("uk_crew_assignment_entry_crew");
	}

	@Test
	void secondLogForSameSiteAndDayIsRejected() {
		long site = fixtures.newSite();
		fixtures.newDraftLog(site);

		assertThatThrownBy(() -> fixtures.newDraftLog(site))
			.isInstanceOf(DuplicateKeyException.class)
			.hasMessageContaining("uk_daily_log_site_date");
	}

	@Test
	void unknownWeatherIsRejected() {
		long site = fixtures.newSite();

		assertCheckViolation(() -> jdbc.update("""
				INSERT INTO daily_log (site_id, log_date, weather) VALUES (?, DATE '2026-11-02', 'HAIL')""", site),
				"ck_daily_log_weather");
	}

	@Test
	void signedOffLogWithoutSignerIsRejected() {
		long log = fixtures.newDraftLog(fixtures.newSite());

		assertCheckViolation(() -> jdbc.update("UPDATE daily_log SET status = 'SIGNED_OFF' WHERE id = ?", log),
				"ck_daily_log_signed_off");
	}

	@Test
	void workEntryEndingBeforeItStartsIsRejected() {
		long log = fixtures.newDraftLog(fixtures.newSite());

		assertCheckViolation(() -> jdbc.update("""
				INSERT INTO work_entry (daily_log_id, description, start_time, end_time)
				VALUES (?, 'Backwards', TIME '15:00', TIME '07:00')""", log), "ck_work_entry_times");
	}

	@Test
	void siteEndingBeforeItStartsIsRejected() {
		long manager = fixtures.userId(DbFixtures.MANAGER_EMAIL);

		assertCheckViolation(() -> jdbc.update("""
				INSERT INTO site (code, name, address, start_date, end_date, manager_id)
				VALUES ('T-DATES', 'Bad dates', 'Street 1', DATE '2026-05-01', DATE '2026-04-01', ?)""", manager),
				"ck_site_dates");
	}

	@Test
	void unsupportedLanguageIsRejected() {
		assertCheckViolation(() -> jdbc.update("""
				INSERT INTO app_user (email, password_hash, full_name, preferred_language, role_id)
				VALUES ('lang@sitelog.test', 'x', 'Lang Test', 'de', (SELECT id FROM role WHERE name = 'INSPECTOR'))"""),
				"ck_app_user_language");
	}

	@Test
	void unknownIncidentCategoryIsRejected() {
		assertCheckViolation(() -> insertIncident("LOW", "EARTHQUAKE", "REPORTED", null),
				"ck_incident_category");
	}

	@Test
	void highIncidentCannotBeSignedOffWithoutSeverityFields() {
		assertCheckViolation(() -> insertIncident("HIGH", "MACHINERY", "SIGNED_OFF", null),
				"ck_incident_severity_fields");
	}

	@Test
	void highIncidentWithAllSeverityFieldsCanBeSignedOff() {
		assertThat(insertIncident("HIGH", "MACHINERY", "SIGNED_OFF", "Guard removed")).isEqualTo(1);
	}

	@Test
	void lowIncidentCanBeSignedOffWithoutSeverityFields() {
		assertThat(insertIncident("LOW", "NEAR_MISS", "SIGNED_OFF", null)).isEqualTo(1);
	}

	@Test
	void doneCorrectiveActionWithoutCompletionTimeIsRejected() {
		insertIncident("LOW", "OTHER", "REPORTED", null);
		long incident = jdbc.queryForObject("SELECT max(id) FROM incident", Long.class);

		assertCheckViolation(() -> jdbc.update("""
				INSERT INTO corrective_action (incident_id, description, due_date, status)
				VALUES (?, 'Fix it', DATE '2026-12-01', 'DONE')""", incident), "ck_corrective_action_completed");
	}

	@Test
	void blankReopenReasonIsRejected() {
		long log = fixtures.newDraftLog(fixtures.newSite());
		long manager = fixtures.userId(DbFixtures.MANAGER_EMAIL);

		assertCheckViolation(() -> jdbc.update("""
				INSERT INTO log_reopen_request (daily_log_id, reason, requested_by) VALUES (?, '   ', ?)""",
				log, manager), "ck_log_reopen_request_reason");
	}

	@Test
	void secondPendingReopenRequestForSameLogIsRejected() {
		long log = fixtures.newDraftLog(fixtures.newSite());
		long manager = fixtures.userId(DbFixtures.MANAGER_EMAIL);
		String insert = "INSERT INTO log_reopen_request (daily_log_id, reason, requested_by) VALUES (?, 'Typo', ?)";
		jdbc.update(insert, log, manager);

		assertThatThrownBy(() -> jdbc.update(insert, log, manager))
			.isInstanceOf(DuplicateKeyException.class)
			.hasMessageContaining("uk_log_reopen_request_pending");
	}

	private int insertIncident(String severity, String category, String status, String rootCause) {
		long site = fixtures.newSite();
		long manager = fixtures.userId(DbFixtures.MANAGER_EMAIL);
		boolean signed = !status.equals("REPORTED") && !status.equals("UNDER_REVIEW");
		String detail = rootCause == null ? null : "Witness A";
		return jdbc.update("""
				INSERT INTO incident (site_id, occurred_at, title, description, severity, category, status,
				                      root_cause, witnesses, medical_treatment, authority_notified,
				                      reported_by, signed_off_by, signed_off_at)
				VALUES (?, now(), 'Test incident', 'Something happened', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
				site, severity, category, status, rootCause, detail, detail, rootCause == null ? null : true,
				manager, signed ? manager : null, signed ? java.sql.Timestamp.from(java.time.Instant.now()) : null);
	}

}
