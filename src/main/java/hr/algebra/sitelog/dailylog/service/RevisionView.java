package hr.algebra.sitelog.dailylog.service;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * One Envers revision (one committed transaction) that touched the log or its children.
 *
 * @param username e-mail stored in {@code revinfo} ({@code system} for changes without a user)
 * @param userName full name of that user, or null when there is none (system / unknown)
 */
public record RevisionView(Long revision, ZonedDateTime changedAt, String username, String userName,
		List<EntityChange> changes) {
}
