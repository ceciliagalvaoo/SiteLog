package hr.algebra.sitelog.incident.domain;

/**
 * Incident workflow (AR2): {@code REPORTED -> UNDER_REVIEW -> SIGNED_OFF -> CLOSED}.
 * Values match {@code ck_incident_status} in V3.
 */
public enum IncidentStatus {
	REPORTED,
	UNDER_REVIEW,
	SIGNED_OFF,
	CLOSED;

	/** Still being worked on: counted as "open" in the site KPIs. */
	public boolean isOpen() {
		return this == REPORTED || this == UNDER_REVIEW;
	}
}
