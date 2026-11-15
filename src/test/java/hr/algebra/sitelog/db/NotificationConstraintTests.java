package hr.algebra.sitelog.db;

import static hr.algebra.sitelog.db.DbFixtures.assertCheckViolation;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.TestcontainersConfiguration;
import hr.algebra.sitelog.notification.domain.NotificationType;

/**
 * The CHECK constraints of V6 on {@code notification}: known type, non-blank title and a link
 * that is an application-relative path (never an external URL or a protocol-relative
 * {@code //host}, so a notification can never become an open redirect). Each test is rolled back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class NotificationConstraintTests {

	private final JdbcTemplate jdbc;

	private final long recipient;

	@Autowired
	NotificationConstraintTests(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
		this.recipient = new DbFixtures(jdbc).userId("manager@sitelog.test");
	}

	private int insert(String type, String title, String link) {
		return jdbc.update("INSERT INTO notification (recipient_id, type, title, link) VALUES (?, ?, ?, ?)", recipient,
				type, title, link);
	}

	@Test
	void everyJavaNotificationTypeIsAccepted() {
		for (NotificationType type : NotificationType.values()) {
			assertThat(insert(type.name(), "Title", "/daily-logs/1")).isEqualTo(1);
		}
	}

	@Test
	void unknownTypeIsRejected() {
		assertCheckViolation(() -> insert("EMAIL_SENT", "Title", null), "ck_notification_type");
	}

	@Test
	void lowerCaseTypeIsRejected() {
		assertCheckViolation(() -> insert("daily_log_unsigned", "Title", null), "ck_notification_type");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void blankTitleIsRejected(String title) {
		assertCheckViolation(() -> insert("DAILY_LOG_UNSIGNED", title, null), "ck_notification_title");
	}

	@ParameterizedTest
	@ValueSource(strings = { "//evil.example/phish", "https://evil.example", "daily-logs/1", "javascript:alert(1)" })
	void nonRelativeLinkIsRejected(String link) {
		assertCheckViolation(() -> insert("DAILY_LOG_UNSIGNED", "Title", link), "ck_notification_link_relative");
	}

	@Test
	void relativeLinkAndMissingLinkAreAccepted() {
		assertThat(insert("REOPEN_REQUESTED", "Reopen requested", "/daily-logs/42?tab=history")).isEqualTo(1);
		assertThat(insert("HIGH_SEVERITY_INCIDENT", "High severity", null)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE recipient_id = ? AND read_at IS NULL"
				+ " AND created_at IS NOT NULL AND type IN ('REOPEN_REQUESTED', 'HIGH_SEVERITY_INCIDENT')", Long.class,
				recipient)).isGreaterThanOrEqualTo(2L);
	}

}
