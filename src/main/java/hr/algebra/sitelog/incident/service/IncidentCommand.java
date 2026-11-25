package hr.algebra.sitelog.incident.service;

import java.time.Instant;
import java.util.List;

import hr.algebra.sitelog.incident.domain.IncidentCategory;
import hr.algebra.sitelog.incident.domain.Severity;

/**
 * Data to report or change an incident (from the screen form or, later, the REST request).
 * {@code dailyLogId} is optional; the four last fields are required for HIGH / CRITICAL (AR2).
 */
public record IncidentCommand(Instant occurredAt, String title, String description, Severity severity,
		IncidentCategory category, Long dailyLogId, List<Long> affectedCrewIds, String rootCause, String witnesses,
		String medicalTreatment, Boolean authorityNotified) {

	public IncidentCommand {
		affectedCrewIds = affectedCrewIds == null ? List.of() : List.copyOf(affectedCrewIds);
	}

}
