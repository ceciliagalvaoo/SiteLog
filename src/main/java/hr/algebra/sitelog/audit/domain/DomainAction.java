package hr.algebra.sitelog.audit.domain;

import java.time.Instant;

import hr.algebra.sitelog.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * One intercepted domain action ({@code domain_action}, V7): which action, on which record, by
 * whom, when and with which outcome. Written only by {@code DomainActionRecorder}; the table is
 * append-only (V7 triggers), so the entity has no setters.
 *
 * <p>The actor is kept as a plain id + e-mail, not a {@code @ManyToOne}: the row is a frozen
 * fact and the timeline only needs the name, which the reading service resolves in one query.
 */
@Entity
@Table(name = "domain_action")
public class DomainAction extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, length = 40, updatable = false)
	private DomainActionType action;

	@Enumerated(EnumType.STRING)
	@Column(name = "entity_type", nullable = false, length = 30, updatable = false)
	private AuditedEntityType entityType;

	@Column(name = "entity_id", updatable = false)
	private Long entityId;

	@Column(name = "actor_id", updatable = false)
	private Long actorId;

	@Column(name = "actor_email", updatable = false)
	private String actorEmail;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "outcome", nullable = false, length = 10, updatable = false)
	private ActionOutcome outcome;

	@Column(name = "duration_ms", nullable = false, updatable = false)
	private int durationMs;

	@Column(name = "detail", length = 500, updatable = false)
	private String detail;

	protected DomainAction() {
	}

	public DomainAction(DomainActionType action, Long entityId, Long actorId, String actorEmail, Instant occurredAt,
			ActionOutcome outcome, int durationMs, String detail) {
		this.action = action;
		this.entityType = action.entityType();
		this.entityId = entityId;
		this.actorId = actorId;
		this.actorEmail = actorEmail;
		this.occurredAt = occurredAt;
		this.outcome = outcome;
		this.durationMs = Math.max(durationMs, 0);
		this.detail = detail == null || detail.length() <= 500 ? detail : detail.substring(0, 500);
	}

	public DomainActionType getAction() {
		return action;
	}

	public AuditedEntityType getEntityType() {
		return entityType;
	}

	public Long getEntityId() {
		return entityId;
	}

	public Long getActorId() {
		return actorId;
	}

	public String getActorEmail() {
		return actorEmail;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public ActionOutcome getOutcome() {
		return outcome;
	}

	public int getDurationMs() {
		return durationMs;
	}

	public String getDetail() {
		return detail;
	}

}
