package hr.algebra.sitelog.audit.domain;

/**
 * The domain actions recorded by the {@code @AuditedAction} aspect, each tied to the kind of
 * record it changes. Values match {@code ck_domain_action_action} in V7. Corrective action
 * changes are recorded on their incident, so they appear in the incident's timeline.
 */
public enum DomainActionType {

	LOG_SIGNED_OFF(AuditedEntityType.DAILY_LOG),
	LOG_REOPEN_REQUESTED(AuditedEntityType.DAILY_LOG),
	LOG_REOPEN_APPROVED(AuditedEntityType.DAILY_LOG),
	LOG_REOPEN_REJECTED(AuditedEntityType.DAILY_LOG),

	INCIDENT_REPORTED(AuditedEntityType.INCIDENT),
	INCIDENT_UPDATED(AuditedEntityType.INCIDENT),
	INCIDENT_REVIEW_STARTED(AuditedEntityType.INCIDENT),
	INCIDENT_SIGNED_OFF(AuditedEntityType.INCIDENT),
	INCIDENT_CLOSED(AuditedEntityType.INCIDENT),
	INCIDENT_DELETED(AuditedEntityType.INCIDENT),

	CORRECTIVE_ACTION_ADDED(AuditedEntityType.INCIDENT),
	CORRECTIVE_ACTION_UPDATED(AuditedEntityType.INCIDENT),
	CORRECTIVE_ACTION_DONE(AuditedEntityType.INCIDENT),
	CORRECTIVE_ACTION_DELETED(AuditedEntityType.INCIDENT);

	private final AuditedEntityType entityType;

	DomainActionType(AuditedEntityType entityType) {
		this.entityType = entityType;
	}

	public AuditedEntityType entityType() {
		return entityType;
	}

	/** Message key of the timeline label, {@code audit.action.<NAME>}. */
	public String messageKey() {
		return "audit.action." + name();
	}

}
