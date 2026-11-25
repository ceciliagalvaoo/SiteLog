package hr.algebra.sitelog.notification.service;

import static hr.algebra.sitelog.support.TestEntities.NOW;
import static hr.algebra.sitelog.support.TestEntities.manager;
import static hr.algebra.sitelog.support.TestEntities.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import hr.algebra.sitelog.notification.domain.MessageArgsConverter;
import hr.algebra.sitelog.notification.domain.Notification;
import hr.algebra.sitelog.notification.domain.NotificationType;
import hr.algebra.sitelog.notification.repository.NotificationRepository;
import hr.algebra.sitelog.support.TestEntities;
import hr.algebra.sitelog.user.domain.AppUser;
import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.repository.AppUserRepository;

/** Notifications are stored as message key + arguments, one per active user of the role. */
@ExtendWith(MockitoExtension.class)
class NotificationPublisherTests {

	@Mock
	NotificationRepository notifications;

	@Mock
	AppUserRepository users;

	@Test
	@SuppressWarnings("unchecked")
	void notifyRole_createsOneTranslatableNotificationPerActiveUserOfTheRole() {
		AppUser first = user(9L, "Ivana Babic", RoleName.SAFETY_SUPERVISOR, true);
		AppUser second = user(10L, "Petra Horvat", RoleName.SAFETY_SUPERVISOR, true);
		when(users.findByRole_NameAndActiveTrueOrderByFullNameAsc(RoleName.SAFETY_SUPERVISOR))
			.thenReturn(List.of(first, second));

		int count = new NotificationPublisher(notifications, users, TestEntities.fixedClock()).notifyRole(
				RoleName.SAFETY_SUPERVISOR, NotificationType.REOPEN_REQUESTED, "/daily-logs/10", "ZG-01",
				LocalDate.of(2026, 10, 29), "Marko\u001FKovacevic", null);

		assertThat(count).isEqualTo(2);
		ArgumentCaptor<List<Notification>> saved = ArgumentCaptor.forClass(List.class);
		verify(notifications).saveAll(saved.capture());
		assertThat(saved.getValue()).extracting(Notification::getRecipient).containsExactly(first, second);
		assertThat(saved.getValue()).allSatisfy(n -> {
			assertThat(n.getType()).isEqualTo(NotificationType.REOPEN_REQUESTED);
			assertThat(n.getTitle()).isEqualTo("notification.type.REOPEN_REQUESTED");
			assertThat(n.getBodyKey()).isEqualTo("notification.body.REOPEN_REQUESTED");
			assertThat(n.getBodyArgs()).containsExactly("ZG-01", "2026-10-29", "MarkoKovacevic", "");
			assertThat(n.getBody()).isNull();
			assertThat(n.getLink()).isEqualTo("/daily-logs/10");
			assertThat(n.getCreatedAt()).isEqualTo(NOW);
			assertThat(n.isRead()).isFalse();
		});
	}

	@Test
	void notifyUser_createsOneNotification() {
		AppUser recipient = manager(7L);

		new NotificationPublisher(notifications, users, TestEntities.fixedClock()).notifyUser(recipient,
				NotificationType.DAILY_LOG_UNSIGNED, "/daily-logs/3", "ZG-01", "2026-10-29");

		ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
		verify(notifications).save(saved.capture());
		assertThat(saved.getValue().getRecipient()).isSameAs(recipient);
		assertThat(saved.getValue().getBodyArgs()).containsExactly("ZG-01", "2026-10-29");
	}

	@Test
	void converter_roundTripsTheArgumentsAndStoresNoneAsNull() {
		MessageArgsConverter converter = new MessageArgsConverter();

		String column = converter.convertToDatabaseColumn(List.of("ZG-01", "", "Fall · edge"));

		assertThat(converter.convertToEntityAttribute(column)).containsExactly("ZG-01", "", "Fall · edge");
		assertThat(converter.convertToDatabaseColumn(List.of())).isNull();
		assertThat(converter.convertToDatabaseColumn(null)).isNull();
		assertThat(converter.convertToEntityAttribute(null)).isEmpty();
	}

	@Test
	void view_withoutBodyKey_keepsTheFreeTextConstructor() {
		NotificationView view = new NotificationView(1L, NotificationType.DAILY_LOG_UNSIGNED, "t", "Body", "/x",
				NOW.atZone(TestEntities.ZONE), false);

		assertThat(view.bodyKey()).isNull();
		assertThat(view.bodyArgsArray()).isEmpty();
	}

}
