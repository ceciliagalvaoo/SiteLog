package hr.algebra.sitelog.api.incident;

import java.time.OffsetDateTime;
import java.util.List;

import hr.algebra.sitelog.incident.domain.IncidentCategory;
import hr.algebra.sitelog.incident.domain.Severity;
import hr.algebra.sitelog.incident.service.IncidentCommand;

/** Request body -> service command, shared by create and replace (same trimming as the form). */
final class IncidentRequests {

	private IncidentRequests() {
	}

	static IncidentCommand command(OffsetDateTime occurredAt, String title, String description, Severity severity,
			IncidentCategory category, Long dailyLogId, List<Long> affectedCrewIds, String rootCause,
			String witnesses, String medicalTreatment, Boolean authorityNotified) {
		return new IncidentCommand(occurredAt == null ? null : occurredAt.toInstant(), strip(title), strip(description),
				severity, category, dailyLogId, affectedCrewIds, rootCause, witnesses, medicalTreatment,
				authorityNotified);
	}

	private static String strip(String value) {
		return value == null ? null : value.strip();
	}

}
