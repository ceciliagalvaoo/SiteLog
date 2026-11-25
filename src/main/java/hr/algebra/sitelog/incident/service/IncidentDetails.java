package hr.algebra.sitelog.incident.service;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import hr.algebra.sitelog.audit.service.DomainActionView;
import hr.algebra.sitelog.crew.service.CrewOption;
import hr.algebra.sitelog.incident.domain.IncidentCategory;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.domain.Severity;

/**
 * Incident detail page (and the data loaded into the edit form).
 *
 * @param missingSeverityFields for HIGH / CRITICAL: the required fields still empty
 *                              ({@code rootCause}, {@code witnesses}, {@code medicalTreatment},
 *                              {@code authorityNotified}); empty otherwise
 * @param timeline              recorded workflow actions, oldest first
 */
public record IncidentDetails(
		Long id,
		Long siteId,
		String siteCode,
		String siteName,
		Long dailyLogId,
		LocalDate dailyLogDate,
		ZonedDateTime occurredAt,
		String title,
		String description,
		Severity severity,
		IncidentCategory category,
		IncidentStatus status,
		String rootCause,
		String witnesses,
		String medicalTreatment,
		Boolean authorityNotified,
		String reportedByName,
		String signedOffByName,
		ZonedDateTime signedOffAt,
		Long version,
		List<CrewOption> affectedCrews,
		List<CorrectiveActionView> correctiveActions,
		List<String> missingSeverityFields,
		List<DomainActionView> timeline) {

	/** HIGH / CRITICAL: extra fields and a SAFETY_SUPERVISOR sign-off are required (AR2). */
	public boolean requiresSupervisorSignOff() {
		return severity.requiresExtendedFields();
	}

	/** The report can still be changed (REPORTED or UNDER_REVIEW). */
	public boolean editable() {
		return status.isOpen();
	}

	public boolean canStartReview() {
		return status == IncidentStatus.REPORTED;
	}

	/** UNDER_REVIEW with every field the severity requires. */
	public boolean canSignOff() {
		return status == IncidentStatus.UNDER_REVIEW && missingSeverityFields.isEmpty();
	}

	/** SIGNED_OFF and every corrective action DONE. */
	public boolean canClose() {
		return status == IncidentStatus.SIGNED_OFF && openActionCount() == 0;
	}

	/** Only a REPORTED incident without corrective actions may be deleted. */
	public boolean deletable() {
		return status == IncidentStatus.REPORTED && correctiveActions.isEmpty();
	}

	/** Corrective actions can be added and changed until the incident is CLOSED. */
	public boolean actionsEditable() {
		return status != IncidentStatus.CLOSED;
	}

	public long openActionCount() {
		return correctiveActions.stream().filter(action -> !action.done()).count();
	}

}
