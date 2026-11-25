package hr.algebra.sitelog.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.notification.domain.MessageArgsConverter;
import hr.algebra.sitelog.notification.domain.Notification;
import hr.algebra.sitelog.notification.domain.NotificationType;
import hr.algebra.sitelog.notification.repository.NotificationRepository;
import hr.algebra.sitelog.user.domain.AppUser;
import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.repository.AppUserRepository;

/**
 * Creates notifications. Internal API for other services (reopen requested, high-severity
 * incident) and, in M4, for the scheduled jobs: no {@code @PreAuthorize}, because the caller's
 * own rule already decided that the event may happen, and a job has no signed-in user. Never
 * called from a controller.
 *
 * <p>Joins the caller's transaction ({@code REQUIRED}): if the business change rolls back, its
 * notifications are not sent either. Stores message key + arguments, never text, so every
 * recipient reads it in his or her own language.
 */
@Service
public class NotificationPublisher {

	private final NotificationRepository notifications;

	private final AppUserRepository users;

	private final Clock clock;

	public NotificationPublisher(NotificationRepository notifications, AppUserRepository users, Clock clock) {
		this.notifications = notifications;
		this.users = users;
		this.clock = clock;
	}

	/**
	 * One notification for every active user with the role (e.g. every safety supervisor);
	 * returns how many were created. The body is {@code type.bodyKey()} with {@code args}.
	 */
	@Transactional
	public int notifyRole(RoleName role, NotificationType type, String link, Object... args) {
		List<AppUser> recipients = users.findByRole_NameAndActiveTrueOrderByFullNameAsc(role);
		Instant now = Instant.now(clock);
		List<String> values = Arrays.stream(args).map(MessageArgsConverter::clean).toList();
		notifications.saveAll(recipients.stream()
			.map(user -> Notification.translatable(user, type, type.bodyKey(), values, link, now))
			.toList());
		return recipients.size();
	}

	/** One notification for one user (the assignee of an overdue action, the manager of an unsigned log). */
	@Transactional
	public void notifyUser(AppUser recipient, NotificationType type, String link, Object... args) {
		List<String> values = Arrays.stream(args).map(MessageArgsConverter::clean).toList();
		notifications.save(Notification.translatable(recipient, type, type.bodyKey(), values, link,
				Instant.now(clock)));
	}

}
