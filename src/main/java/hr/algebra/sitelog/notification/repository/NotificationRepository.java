package hr.algebra.sitelog.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import hr.algebra.sitelog.notification.domain.Notification;
import hr.algebra.sitelog.notification.domain.NotificationType;

/**
 * Notification queries. All of them filter by recipient, so a user only ever sees or changes
 * their own notifications. The recipient is never fetched: the bell shows type, title, link and time.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

	/** Unread counter on every page (index {@code ix_notification_recipient_read}). */
	long countByRecipient_IdAndReadAtIsNull(Long recipientId);

	/** Bell dropdown: newest unread notifications. */
	List<Notification> findTop10ByRecipient_IdAndReadAtIsNullOrderByCreatedAtDesc(Long recipientId);

	/** "All notifications" page, read and unread. */
	Page<Notification> findByRecipient_IdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

	/** Loads one notification only if it belongs to the user (mark as read, open). */
	Optional<Notification> findByIdAndRecipient_Id(Long id, Long recipientId);

	/** "Mark all as read" in one UPDATE; returns how many were marked. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Notification n set n.readAt = :now where n.recipient.id = :recipientId and n.readAt is null")
	int markAllRead(@Param("recipientId") Long recipientId, @Param("now") Instant now);

	/**
	 * De-duplication for the scheduled jobs: true when the user already has an unread
	 * notification of this type for the same link, so a job running every hour does not repeat it.
	 */
	boolean existsByRecipient_IdAndTypeAndLinkAndReadAtIsNull(Long recipientId, NotificationType type, String link);

}
