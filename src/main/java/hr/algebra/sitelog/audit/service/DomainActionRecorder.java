package hr.algebra.sitelog.audit.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.audit.domain.ActionOutcome;
import hr.algebra.sitelog.audit.domain.DomainAction;
import hr.algebra.sitelog.audit.domain.DomainActionType;
import hr.algebra.sitelog.audit.repository.DomainActionRepository;

/**
 * Writes the rows of {@code domain_action} for {@code DomainActionAspect}. Internal: no
 * {@code @PreAuthorize}, because it must also record a refused call and calls without a user.
 *
 * <p>{@code REQUIRED}: the aspect runs outside the business transaction, so normally this opens
 * its own short transaction after the business one committed. When the audited method was
 * called from inside another transaction (one service calling another), the row joins that
 * transaction and rolls back with it, so the log never claims an action that was undone.
 */
@Service
public class DomainActionRecorder {

	private final DomainActionRepository actions;

	public DomainActionRecorder(DomainActionRepository actions) {
		this.actions = actions;
	}

	@Transactional
	public void record(DomainActionType action, Long entityId, Long actorId, String actorEmail, Instant occurredAt,
			ActionOutcome outcome, int durationMs, String detail) {
		actions.save(new DomainAction(action, entityId, actorId, actorEmail, occurredAt, outcome, durationMs, detail));
	}

}
