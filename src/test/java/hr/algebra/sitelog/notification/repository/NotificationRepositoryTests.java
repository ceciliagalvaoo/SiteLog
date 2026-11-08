package hr.algebra.sitelog.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import hr.algebra.sitelog.notification.domain.Notification;
import hr.algebra.sitelog.notification.domain.NotificationType;
import hr.algebra.sitelog.support.JpaFixtures;
import hr.algebra.sitelog.support.RepositoryTest;
import hr.algebra.sitelog.user.domain.AppUser;
import jakarta.persistence.EntityManager;

/**
 * {@link NotificationRepository}. The seed has no notifications; each test starts with 12 unread
 * and 1 read notification for the supervisor and 2 unread for the inspector.
 */
@RepositoryTest
class NotificationRepositoryTests {

	private static final Instant T0 = Instant.parse("2026-11-01T08:00:00Z");

	private final NotificationRepository notifications;
	private final JpaFixtures fixtures;

	private AppUser supervisor;
	private AppUser inspector;
	private final List<Notification> supervisorUnread = new ArrayList<>();
	private Notification inspectorNotification;

	@Autowired
	NotificationRepositoryTests(NotificationRepository notifications, EntityManager em) {
		this.notifications = notifications;
		this.fixtures = new JpaFixtures(em);
	}

	@BeforeEach
	void createNotifications() {
		supervisor = fixtures.user(JpaFixtures.SUPERVISOR);
		inspector = fixtures.user(JpaFixtures.INSPECTOR);
		for (int i = 0; i < 12; i++) {
			supervisorUnread.add(notifications.save(notification(supervisor, "/daily-logs/" + i, T0.plusSeconds(60L * i))));
		}
		Notification read = notification(supervisor, "/daily-logs/99", T0.plus(1, ChronoUnit.DAYS));
		read.markRead(T0.plus(2, ChronoUnit.DAYS));
		notifications.save(read);
		inspectorNotification = notifications.save(notification(inspector, "/incidents/1", T0));
		notifications.save(notification(inspector, "/incidents/2", T0.plusSeconds(1)));
		fixtures.flushAndClear();
	}

	@Test
	void countByRecipientIdAndReadAtIsNull_countsOnlyUnreadOfThatRecipient() {
		assertThat(notifications.countByRecipient_IdAndReadAtIsNull(supervisor.getId())).isEqualTo(12);
		assertThat(notifications.countByRecipient_IdAndReadAtIsNull(inspector.getId())).isEqualTo(2);
	}

	@Test
	void findTop10Unread_returnsTenNewestUnreadNewestFirst() {
		List<Notification> top = notifications
			.findTop10ByRecipient_IdAndReadAtIsNullOrderByCreatedAtDesc(supervisor.getId());

		// unread #11 (newest) down to #2; the read one is newer but excluded
		assertThat(top).hasSize(10);
		assertThat(top).extracting(Notification::getLink)
			.containsExactly("/daily-logs/11", "/daily-logs/10", "/daily-logs/9", "/daily-logs/8", "/daily-logs/7",
					"/daily-logs/6", "/daily-logs/5", "/daily-logs/4", "/daily-logs/3", "/daily-logs/2");
		assertThat(top).allSatisfy(n -> assertThat(n.isRead()).isFalse());
	}

	@Test
	void findByRecipientIdOrderByCreatedAtDesc_pagesReadAndUnread() {
		var page = notifications.findByRecipient_IdOrderByCreatedAtDesc(supervisor.getId(), PageRequest.of(0, 5));

		assertThat(page.getTotalElements()).isEqualTo(13);
		assertThat(page.getContent().getFirst().getLink()).isEqualTo("/daily-logs/99");
		assertThat(page.getContent().getFirst().isRead()).isTrue();
	}

	@Test
	void markAllRead_marksOnlyTheRecipientsUnreadAndReturnsTheCount() {
		Instant now = Instant.parse("2026-11-05T10:00:00Z");

		int marked = notifications.markAllRead(supervisor.getId(), now);

		assertThat(marked).isEqualTo(12);
		assertThat(notifications.countByRecipient_IdAndReadAtIsNull(supervisor.getId())).isZero();
		assertThat(notifications.countByRecipient_IdAndReadAtIsNull(inspector.getId())).isEqualTo(2);
		// clearAutomatically: the next read sees the UPDATE, not a stale cached entity
		assertThat(notifications.findById(supervisorUnread.getFirst().getId()).orElseThrow().getReadAt())
			.isEqualTo(now);
		// the notification that was already read keeps its original read time
		assertThat(notifications.findByRecipient_IdOrderByCreatedAtDesc(supervisor.getId(), PageRequest.of(0, 1))
			.getContent().getFirst().getReadAt()).isEqualTo(T0.plus(2, ChronoUnit.DAYS));
	}

	@Test
	void markAllRead_whenNothingUnread_returnsZero() {
		notifications.markAllRead(inspector.getId(), Instant.now());

		assertThat(notifications.markAllRead(inspector.getId(), Instant.now())).isZero();
	}

	@Test
	void findByIdAndRecipientId_doesNotReturnAnotherUsersNotification() {
		long id = inspectorNotification.getId();

		assertThat(notifications.findByIdAndRecipient_Id(id, inspector.getId())).isPresent();
		assertThat(notifications.findByIdAndRecipient_Id(id, supervisor.getId())).isEmpty();
	}

	@Test
	void existsUnreadByRecipientTypeAndLink_deduplicatesUntilRead() {
		assertThat(notifications.existsByRecipient_IdAndTypeAndLinkAndReadAtIsNull(supervisor.getId(),
				NotificationType.DAILY_LOG_UNSIGNED, "/daily-logs/3")).isTrue();
		// same link but already read, other type, other recipient: no duplicate
		assertThat(notifications.existsByRecipient_IdAndTypeAndLinkAndReadAtIsNull(supervisor.getId(),
				NotificationType.DAILY_LOG_UNSIGNED, "/daily-logs/99")).isFalse();
		assertThat(notifications.existsByRecipient_IdAndTypeAndLinkAndReadAtIsNull(supervisor.getId(),
				NotificationType.REOPEN_REQUESTED, "/daily-logs/3")).isFalse();
		assertThat(notifications.existsByRecipient_IdAndTypeAndLinkAndReadAtIsNull(inspector.getId(),
				NotificationType.DAILY_LOG_UNSIGNED, "/daily-logs/3")).isFalse();
	}

	private static Notification notification(AppUser recipient, String link, Instant createdAt) {
		return new Notification(recipient, NotificationType.DAILY_LOG_UNSIGNED, "Daily log not signed off", null,
				link, createdAt);
	}

}
