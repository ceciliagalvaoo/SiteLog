package hr.algebra.sitelog.audit.service;

import java.time.ZonedDateTime;

import hr.algebra.sitelog.audit.domain.DomainActionType;

/**
 * One entry of a record's timeline. {@code messageKey} is {@code audit.action.<ACTION>};
 * {@code actorName} is the user's full name, or null for the system.
 */
public record DomainActionView(DomainActionType action, String messageKey, String actorName,
		ZonedDateTime occurredAt) {

	public static DomainActionView of(DomainActionType action, String actorName, ZonedDateTime occurredAt) {
		return new DomainActionView(action, action.messageKey(), actorName, occurredAt);
	}

}
