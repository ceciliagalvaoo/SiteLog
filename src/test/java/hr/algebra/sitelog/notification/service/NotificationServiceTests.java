package hr.algebra.sitelog.notification.service;

import static hr.algebra.sitelog.support.TestEntities.NOW;
import static hr.algebra.sitelog.support.TestEntities.ZONE;
import static hr.algebra.sitelog.support.TestEntities.manager;
import static hr.algebra.sitelog.support.TestEntities.notification;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.notification.domain.Notification;
import hr.algebra.sitelog.notification.domain.NotificationType;
import hr.algebra.sitelog.notification.repository.NotificationRepository;
import hr.algebra.sitelog.support.TestEntities;
import hr.algebra.sitelog.user.domain.AppUser;
import hr.algebra.sitelog.user.service.CurrentUserProvider;

/** Every query is scoped to the current user: nobody reads or marks someone else's notification. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {

	private static final Long ME = 7L;

	@Mock
	NotificationRepository notifications;

	@Mock
	CurrentUserProvider currentUser;

	NotificationService service;

	AppUser me;

	@BeforeEach
	void setUp() {
		service = new NotificationService(notifications, currentUser, TestEntities.fixedClock());
		me = manager(ME);
		when(currentUser.currentUserId()).thenReturn(ME);
	}

	@Test
	void unreadCount_countsUnreadOfTheCurrentUser() {
		when(notifications.countByRecipient_IdAndReadAtIsNull(ME)).thenReturn(3L);

		assertThat(service.unreadCount()).isEqualTo(3L);
	}

	@Test
	void latestUnread_mapsToViewsWithCreationTimeInSitesZone() {
		Instant created = Instant.parse("2026-11-14T22:30:00Z");
		when(notifications.findTop10ByRecipient_IdAndReadAtIsNullOrderByCreatedAtDesc(ME))
			.thenReturn(List.of(notification(1L, me, "/daily-logs/10", created)));

		List<NotificationView> views = service.latestUnread();

		assertThat(views).singleElement().satisfies(view -> {
			assertThat(view.id()).isEqualTo(1L);
			assertThat(view.type()).isEqualTo(NotificationType.DAILY_LOG_UNSIGNED);
			assertThat(view.link()).isEqualTo("/daily-logs/10");
			assertThat(view.read()).isFalse();
			assertThat(view.createdAt().getZone()).isEqualTo(ZONE);
			assertThat(view.createdAt().toLocalDate()).hasToString("2026-11-14");
			assertThat(view.createdAt().getHour()).isEqualTo(23);
		});
	}

	@Test
	void list_returnsPageOfTheCurrentUser() {
		PageRequest page = PageRequest.of(0, 20);
		Notification read = notification(2L, me, null, NOW.minusSeconds(60));
		read.markRead(NOW);
		when(notifications.findByRecipient_IdOrderByCreatedAtDesc(ME, page)).thenReturn(new PageImpl<>(List.of(read)));

		Page<NotificationView> result = service.list(page);

		assertThat(result.getContent()).singleElement().satisfies(view -> {
			assertThat(view.read()).isTrue();
			assertThat(view.link()).isNull();
		});
	}

	@Test
	void markRead_ofOwnNotification_stampsClockTimeAndReturnsItsLink() {
		Notification notification = notification(1L, me, "/daily-logs/10", NOW.minusSeconds(3600));
		when(notifications.findByIdAndRecipient_Id(1L, ME)).thenReturn(Optional.of(notification));

		String link = service.markRead(1L);

		assertThat(link).isEqualTo("/daily-logs/10");
		assertThat(notification.getReadAt()).isEqualTo(NOW);
	}

	@Test
	void markRead_ofAlreadyReadNotification_keepsTheFirstReadTime() {
		Instant firstRead = NOW.minusSeconds(600);
		Notification notification = notification(1L, me, "/x", NOW.minusSeconds(3600));
		notification.markRead(firstRead);
		when(notifications.findByIdAndRecipient_Id(1L, ME)).thenReturn(Optional.of(notification));

		service.markRead(1L);

		assertThat(notification.getReadAt()).isEqualTo(firstRead);
	}

	@Test
	void markRead_ofSomeoneElsesNotification_throwsNotFoundException() {
		when(notifications.findByIdAndRecipient_Id(5L, ME)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.markRead(5L)).isInstanceOf(NotFoundException.class)
			.hasMessageContaining("Notification 5");
	}

	@Test
	void markAllRead_updatesInOneStatementWithClockTimeAndReturnsCount() {
		when(notifications.markAllRead(ME, NOW)).thenReturn(4);

		assertThat(service.markAllRead()).isEqualTo(4);
		verify(notifications).markAllRead(ME, NOW);
	}

}
