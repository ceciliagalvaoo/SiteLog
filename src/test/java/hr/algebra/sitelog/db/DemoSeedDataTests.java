package hr.algebra.sitelog.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.TestcontainersConfiguration;

/**
 * Checks that the V4 demo data covers what the demo and the grading need:
 * one account per role with the documented password, logs in every state,
 * incidents of every severity and status, and corrective actions in every status.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DemoSeedDataTests {

	private static final String DEMO_PASSWORD = "SiteLog-demo-2026";

	private final JdbcTemplate jdbc;

	@Autowired
	DemoSeedDataTests(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Test
	void everyRoleHasAnActiveDemoAccountWithTheDocumentedPassword() {
		List<String> roles = jdbc.queryForList("""
				SELECT DISTINCT r.name FROM app_user u JOIN role r ON r.id = u.role_id
				WHERE u.email LIKE '%@sitelog.test' AND u.active""", String.class);
		assertThat(roles).containsExactlyInAnyOrder("ADMIN", "SITE_MANAGER", "SAFETY_SUPERVISOR", "INSPECTOR");

		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		List<String> hashes = jdbc.queryForList(
				"SELECT password_hash FROM app_user WHERE email LIKE '%@sitelog.test'", String.class);
		assertThat(hashes).allSatisfy(hash -> assertThat(encoder.matches(DEMO_PASSWORD, hash)).isTrue());
	}

	@Test
	void sitesAreInZagrebAndSplit() {
		assertThat(jdbc.queryForList("SELECT code FROM site WHERE code IN ('ZG-01', 'ZG-02', 'ST-01')", String.class))
			.hasSize(3);
	}

	@Test
	void dailyLogsExistInEveryStatusWithWorkAndCrews() {
		assertThat(distinct("SELECT DISTINCT status FROM daily_log"))
			.containsExactlyInAnyOrder("DRAFT", "SIGNED_OFF", "REOPENED");
		Integer logsWithoutCrews = jdbc.queryForObject("""
				SELECT count(*) FROM daily_log dl
				WHERE NOT EXISTS (SELECT 1 FROM work_entry we JOIN crew_assignment ca ON ca.work_entry_id = we.id
				                  WHERE we.daily_log_id = dl.id)""", Integer.class);
		assertThat(logsWithoutCrews).isZero();
	}

	@Test
	void reopenedLogHasAnApprovedRequestAndARevision() {
		Integer reopenedWithApproval = jdbc.queryForObject("""
				SELECT count(*) FROM daily_log dl
				JOIN log_reopen_request r ON r.daily_log_id = dl.id AND r.status = 'APPROVED'
				WHERE dl.status = 'REOPENED' AND dl.revision = 1""", Integer.class);
		assertThat(reopenedWithApproval).isEqualTo(1);
	}

	@Test
	void incidentsCoverEverySeverityAndStatus() {
		assertThat(distinct("SELECT DISTINCT severity FROM incident"))
			.containsExactlyInAnyOrder("LOW", "MEDIUM", "HIGH", "CRITICAL");
		assertThat(distinct("SELECT DISTINCT status FROM incident"))
			.containsExactlyInAnyOrder("REPORTED", "UNDER_REVIEW", "SIGNED_OFF", "CLOSED");
		assertThat(distinct("SELECT DISTINCT status FROM corrective_action"))
			.containsExactlyInAnyOrder("OPEN", "DONE", "OVERDUE");
	}

	@Test
	void aiDraftIsWaitingForReview() {
		assertThat(distinct("SELECT DISTINCT status FROM incident_draft")).contains("PENDING_REVIEW");
	}

	private List<String> distinct(String sql) {
		return jdbc.queryForList(sql, String.class);
	}

}
