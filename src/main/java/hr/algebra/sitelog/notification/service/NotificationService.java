package hr.algebra.sitelog.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.notification.domain.Notification;
import hr.algebra.sitelog.notification.repository.NotificationRepository;
import hr.algebra.sitelog.user.service.CurrentUserProvider;

/**
 * In-app notifications of the current user (bell in the top bar). Every query filters by the
 * current user, so nobody can read or mark someone else's notification. Creating notifications
 * (scheduled jobs, reopen requests, high-severity incidents) comes with those features (M3/M4).
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

	/** How many unread notifications the bell dropdown lists. */
	public static final int BELL_SIZE = 10;

	private final NotificationRepository notifications;

	private final CurrentUserProvider currentUser;

	private final Clock clock;

	public NotificationService(NotificationRepository notifications, CurrentUserProvider currentUser, Clock clock) {
		this.notifications = notifications;
		this.currentUser = currentUser;
		this.clock = clock;
	}

	public long unreadCount() {
		return notifications.countByRecipient_IdAndReadAtIsNull(currentUser.currentUserId());
	}

	/** Newest {@value #BELL_SIZE} unread notifications. */
	public List<NotificationView> latestUnread() {
		return notifications.findTop10ByRecipient_IdAndReadAtIsNullOrderByCreatedAtDesc(currentUser.currentUserId())
			.stream().map(this::toView).toList();
	}

	public Page<NotificationView> list(Pageable pageable) {
		return notifications.findByRecipient_IdOrderByCreatedAtDesc(currentUser.currentUserId(), pageable)
			.map(this::toView);
	}

	/** Marks one of the user's notifications as read; returns its link (where to go next), may be null. */
	@Transactional
	public String markRead(Long id) {
		Notification notification = notifications.findByIdAndRecipient_Id(id, currentUser.currentUserId())
			.orElseThrow(() -> new NotFoundException(Notification.class, id));
		notification.markRead(Instant.now(clock));
		return notification.getLink();
	}

	/** Marks all unread notifications of the user as read in one UPDATE; returns how many. */
	@Transactional
	public int markAllRead() {
		return notifications.markAllRead(currentUser.currentUserId(), Instant.now(clock));
	}

	private NotificationView toView(Notification n) {
		return new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(),
				n.getCreatedAt().atZone(clock.getZone()), n.isRead());
	}

}
