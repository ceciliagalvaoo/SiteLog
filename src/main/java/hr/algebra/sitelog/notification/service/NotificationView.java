package hr.algebra.sitelog.notification.service;

import java.time.ZonedDateTime;

import hr.algebra.sitelog.notification.domain.NotificationType;

/**
 * A notification as the bell and the notifications page show it. {@code createdAt} is zoned
 * (sites' time zone) because {@code #temporals.format} cannot format an {@code Instant}.
 * The template takes the title from {@code notification.type.<TYPE>} so it follows the UI
 * language; {@code title} is the stored fallback.
 */
public record NotificationView(Long id, NotificationType type, String title, String body, String link,
		ZonedDateTime createdAt, boolean read) {
}
