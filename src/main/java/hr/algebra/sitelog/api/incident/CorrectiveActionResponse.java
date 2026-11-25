package hr.algebra.sitelog.api.incident;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import hr.algebra.sitelog.incident.domain.CorrectiveActionStatus;
import hr.algebra.sitelog.incident.service.CorrectiveActionView;
import io.swagger.v3.oas.annotations.media.Schema;

/** A corrective action with the version of its incident (the aggregate root every change must send back). */
@Schema(description = "Corrective action of an incident")
public record CorrectiveActionResponse(
		Long id,
		String description,
		@Schema(nullable = true) Long assigneeId,
		@Schema(nullable = true) String assigneeName,
		LocalDate dueDate,
		@Schema(description = "OPEN, OVERDUE or DONE") CorrectiveActionStatus status,
		@Schema(nullable = true) OffsetDateTime completedAt,
		@Schema(description = "OVERDUE, or still OPEN after its due date") boolean overdue,
		@Schema(description = "Current version of the incident: send it as incidentVersion on the next change")
		Long incidentVersion) {

	static CorrectiveActionResponse of(CorrectiveActionView action, Long incidentVersion) {
		return new CorrectiveActionResponse(action.id(), action.description(), action.assigneeId(),
				action.assigneeName(), action.dueDate(), action.status(),
				action.completedAt() == null ? null : action.completedAt().toOffsetDateTime(), action.overdue(),
				incidentVersion);
	}

}
