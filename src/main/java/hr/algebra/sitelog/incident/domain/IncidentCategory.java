package hr.algebra.sitelog.incident.domain;

/** Incident category. Values match {@code ck_incident_category} (and the AI draft's) in V3. */
public enum IncidentCategory {
	FALL_FROM_HEIGHT,
	STRUCK_BY_OBJECT,
	MACHINERY,
	ELECTRICAL,
	MANUAL_HANDLING,
	SLIP_TRIP,
	FIRE_EXPLOSION,
	ENVIRONMENTAL,
	NEAR_MISS,
	OTHER
}
