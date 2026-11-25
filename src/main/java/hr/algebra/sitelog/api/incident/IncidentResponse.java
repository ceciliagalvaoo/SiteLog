package hr.algebra.sitelog.api.incident;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import hr.algebra.sitelog.audit.domain.DomainActionType;
import hr.algebra.sitelog.audit.service.DomainActionView;
import hr.algebra.sitelog.crew.service.CrewOption;
import hr.algebra.sitelog.incident.domain.IncidentCategory;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.domain.Severity;
import hr.algebra.sitelog.incident.service.IncidentDetails;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The whole Incident aggregate: the report, the AR2 fields, who signed it, its affected crews,
 * corrective actions and timeline, plus what the workflow allows next (the same flags the
 * detail screen uses for its buttons; the role check is still the service's).
 */
@Schema(description = "Incident with its corrective actions and timeline")
public record IncidentResponse(
		Long id,
		Long siteId,
		String siteCode,
		String siteName,
		@Schema(nullable = true) Long dailyLogId,
		@Schema(nullable = true) LocalDate dailyLogDate,
		OffsetDateTime occurredAt,
		String title,
		String description,
		Severity severity,
		IncidentCategory category,
		@Schema(description = "REPORTED, UNDER_REVIEW, SIGNED_OFF or CLOSED") IncidentStatus status,
		@Schema(nullable = true) String rootCause,
		@Schema(nullable = true) String witnesses,
		@Schema(nullable = true) String medicalTreatment,
		@Schema(nullable = true) Boolean authorityNotified,
		String reportedByName,
		@Schema(nullable = true) String signedOffByName,
		@Schema(nullable = true) OffsetDateTime signedOffAt,
		@Schema(description = "Optimistic-locking version: send it back on PUT, DELETE, workflow steps and corrective action changes")
		Long version,
		@Schema(description = "HIGH / CRITICAL: the extra fields and a safety supervisor sign-off are required (AR2)")
		boolean requiresSupervisorSignOff,
		@Schema(description = "HIGH / CRITICAL fields still empty", example = "[\"rootCause\",\"witnesses\"]")
		List<String> missingSeverityFields,
		boolean editable,
		boolean deletable,
		boolean canStartReview,
		boolean canSignOff,
		boolean canClose,
		List<AffectedCrew> affectedCrews,
		List<CorrectiveActionResponse> correctiveActions,
		List<TimelineEntry> timeline) {

	/** A crew involved in the incident. */
	public record AffectedCrew(Long id, String name, String trade, boolean active) {

		static AffectedCrew of(CrewOption crew) {
			return new AffectedCrew(crew.id(), crew.name(), crew.trade(), crew.active());
		}

	}

	/** One recorded workflow action, oldest first. */
	public record TimelineEntry(DomainActionType action, @Schema(nullable = true) String actorName,
			OffsetDateTime occurredAt) {

		static TimelineEntry of(DomainActionView action) {
			return new TimelineEntry(action.action(), action.actorName(), action.occurredAt().toOffsetDateTime());
		}

	}

	static IncidentResponse of(IncidentDetails incident) {
		return new IncidentResponse(incident.id(), incident.siteId(), incident.siteCode(), incident.siteName(),
				incident.dailyLogId(), incident.dailyLogDate(), incident.occurredAt().toOffsetDateTime(),
				incident.title(), incident.description(), incident.severity(), incident.category(), incident.status(),
				incident.rootCause(), incident.witnesses(), incident.medicalTreatment(), incident.authorityNotified(),
				incident.reportedByName(), incident.signedOffByName(),
				incident.signedOffAt() == null ? null : incident.signedOffAt().toOffsetDateTime(), incident.version(),
				incident.requiresSupervisorSignOff(), incident.missingSeverityFields(), incident.editable(),
				incident.deletable(), incident.canStartReview(), incident.canSignOff(), incident.canClose(),
				incident.affectedCrews().stream().map(AffectedCrew::of).toList(),
				incident.correctiveActions().stream()
					.map(action -> CorrectiveActionResponse.of(action, incident.version()))
					.toList(),
				incident.timeline().stream().map(TimelineEntry::of).toList());
	}

}
