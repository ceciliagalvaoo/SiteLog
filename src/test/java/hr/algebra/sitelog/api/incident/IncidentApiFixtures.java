package hr.algebra.sitelog.api.incident;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import hr.algebra.sitelog.audit.domain.DomainActionType;
import hr.algebra.sitelog.audit.service.DomainActionView;
import hr.algebra.sitelog.crew.service.CrewOption;
import hr.algebra.sitelog.incident.domain.CorrectiveActionStatus;
import hr.algebra.sitelog.incident.domain.IncidentCategory;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.domain.Severity;
import hr.algebra.sitelog.incident.service.CorrectiveActionView;
import hr.algebra.sitelog.incident.service.IncidentDetails;
import hr.algebra.sitelog.incident.service.IncidentListItem;

/** Service views and request bodies shared by the incident API slice tests. */
final class IncidentApiFixtures {

	static final ZoneId ZONE = ZoneId.of("Europe/Zagreb");

	static final ZonedDateTime OCCURRED = ZonedDateTime.of(2026, 11, 20, 9, 30, 0, 0, ZONE);

	static final String REPORT_BODY = """
			{"occurredAt":"2026-11-20T09:30:00+01:00","title":"  Slip on formwork ","description":"Wet deck",
			 "severity":"HIGH","category":"SLIP_TRIP","affectedCrewIds":[1],
			 "rootCause":"No mats","witnesses":"None","medicalTreatment":"First aid","authorityNotified":false}""";

	static final String UPDATE_BODY = """
			{"version":2,"occurredAt":"2026-11-20T09:30:00+01:00","title":"Slip on formwork","description":"Wet deck",
			 "severity":"LOW","category":"SLIP_TRIP"}""";

	static final String ACTION_BODY = """
			{"incidentVersion":3,"description":"Install anti-slip mats","assigneeId":7,"dueDate":"2026-12-01"}""";

	private IncidentApiFixtures() {
	}

	static CorrectiveActionView action(Long id, CorrectiveActionStatus status) {
		return new CorrectiveActionView(id, "Install anti-slip mats", 7L, "Marko Kovacevic", LocalDate.of(2026, 12, 1),
				status, status == CorrectiveActionStatus.DONE ? OCCURRED.plusDays(2) : null, false);
	}

	static IncidentDetails incident(Long id, Severity severity, IncidentStatus status, long version,
			List<String> missing) {
		boolean signed = status == IncidentStatus.SIGNED_OFF || status == IncidentStatus.CLOSED;
		return new IncidentDetails(id, 3L, "ZG-01", "Tower A", null, null, OCCURRED, "Slip on formwork", "Wet deck",
				severity, IncidentCategory.SLIP_TRIP, status, "No mats", "None", "First aid", false, "Marko Kovacevic",
				signed ? "Sanja Babic" : null, signed ? OCCURRED.plusHours(3) : null, version,
				List.of(new CrewOption(1L, "Concrete crew A", "Concrete", true)),
				List.of(action(5L, CorrectiveActionStatus.OPEN)), missing,
				List.of(DomainActionView.of(DomainActionType.INCIDENT_REPORTED, "Marko Kovacevic", OCCURRED)));
	}

	static IncidentListItem row(Long id) {
		return new IncidentListItem(id, 3L, "ZG-01", "Tower A", OCCURRED, "Slip on formwork", Severity.HIGH,
				IncidentCategory.SLIP_TRIP, IncidentStatus.REPORTED, "Marko Kovacevic");
	}

}
