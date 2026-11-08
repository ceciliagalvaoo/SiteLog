package hr.algebra.sitelog.notification.domain;

import java.time.Instant;

import hr.algebra.sitelog.common.domain.BaseEntity;
import hr.algebra.sitelog.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * In-app notification for one user (bell icon in the top bar). Unread while {@link #readAt} is
 * null; the index {@code ix_notification_recipient_read (recipient_id, read_at)} serves the
 * unread list and counter.
 */
@Entity
@Table(name = "notification")
public class Notification extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recipient_id", nullable = false)
	private AppUser recipient;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 40)
	private NotificationType type;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "body", length = 1000)
	private String body;

	/** Relative application path, e.g. {@code /daily-logs/42} ({@code ck_notification_link_relative}). */
	@Column(name = "link", length = 500)
	private String link;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "read_at")
	private Instant readAt;

	protected Notification() {
	}

	public Notification(AppUser recipient, NotificationType type, String title, String body, String link,
			Instant createdAt) {
		this.recipient = recipient;
		this.type = type;
		this.title = title;
		this.body = body;
		this.link = link;
		this.createdAt = createdAt;
	}

	/** Marks the notification as read; reading it again keeps the first time. */
	public void markRead(Instant when) {
		if (readAt == null) {
			readAt = when;
		}
	}

	public boolean isRead() {
		return readAt != null;
	}

	public AppUser getRecipient() {
		return recipient;
	}

	public NotificationType getType() {
		return type;
	}

	public String getTitle() {
		return title;
	}

	public String getBody() {
		return body;
	}

	public String getLink() {
		return link;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getReadAt() {
		return readAt;
	}

}
