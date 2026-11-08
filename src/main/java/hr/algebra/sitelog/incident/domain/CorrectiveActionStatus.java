package hr.algebra.sitelog.incident.domain;

/**
 * Corrective action state. OVERDUE is set by the scheduled job when the due date has passed.
 * Values match {@code ck_corrective_action_status} in V3.
 */
public enum CorrectiveActionStatus {
	OPEN,
	DONE,
	OVERDUE
}
