package hr.algebra.sitelog.incident.domain;

/** Incident severity. Values match {@code ck_incident_severity} (and the AI draft's) in V3. */
public enum Severity {
	LOW,
	MEDIUM,
	HIGH,
	CRITICAL;

	/** HIGH and CRITICAL need root cause, witnesses, medical treatment and authority notification before sign-off (AR2). */
	public boolean requiresExtendedFields() {
		return this == HIGH || this == CRITICAL;
	}
}
