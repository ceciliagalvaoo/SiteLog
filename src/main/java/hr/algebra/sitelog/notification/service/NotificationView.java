package hr.algebra.sitelog.notification.service;

import java.time.ZonedDateTime;
import java.util.List;

import hr.algebra.sitelog.notification.domain.NotificationType;

/**
 * A notification as the bell and the notifications page show it. {@code createdAt} is zoned
 * (sites' time zone) because {@code #temporals.format} cannot format an {@code Instant}.
 * The template takes the title from {@code notification.type.<TYPE>} so it follows the UI
 * language; {@code title} is the stored fallback.
 *
 * <p>Body: when {@code bodyKey} is set the template renders
 * {@code #messages.msgWithParams(n.bodyKey, n.bodyArgsArray())} in the reader's language;
 * otherwise it shows the stored free-text {@code body} (rows created before V7).
 */
public record NotificationView(Long id, NotificationType type, String title, String body, String bodyKey,
		List<String> bodyArgs, String link, ZonedDateTime createdAt, boolean read) {

	public NotificationView {
		bodyArgs = bodyArgs == null ? List.of() : List.copyOf(bodyArgs);
	}

	/** A notification with a free-text body (no message key). */
	public NotificationView(Long id, NotificationType type, String title, String body, String link,
			ZonedDateTime createdAt, boolean read) {
		this(id, type, title, body, null, List.of(), link, createdAt, read);
	}

	/** The arguments as an array, the form {@code #messages.msgWithParams} expects. */
	public Object[] bodyArgsArray() {
		return bodyArgs.toArray();
	}

}
