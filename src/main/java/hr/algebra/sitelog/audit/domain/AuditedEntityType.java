package hr.algebra.sitelog.audit.domain;

/** Kind of record a domain action is about. Values match {@code ck_domain_action_entity_type} in V7. */
public enum AuditedEntityType {
	DAILY_LOG,
	INCIDENT
}
