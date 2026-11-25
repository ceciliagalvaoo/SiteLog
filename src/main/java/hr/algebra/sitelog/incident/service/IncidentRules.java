package hr.algebra.sitelog.incident.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

import hr.algebra.sitelog.incident.domain.Incident;
import hr.algebra.sitelog.incident.domain.Severity;

/** Checks shared by the incident and corrective action services. */
final class IncidentRules {

	/** Property names of the AR2 fields, in form order (also the names of the form fields). */
	static final List<String> SEVERITY_FIELDS = List.of("rootCause", "witnesses", "medicalTreatment",
			"authorityNotified");

	private IncidentRules() {
	}

	/**
	 * AR2: which of the extra fields a HIGH / CRITICAL incident still lacks (blank text counts as
	 * missing, like {@code ck_incident_severity_fields}); always empty for LOW / MEDIUM.
	 */
	static List<String> missingSeverityFields(Severity severity, String rootCause, String witnesses,
			String medicalTreatment, Boolean authorityNotified) {
		List<String> missing = new ArrayList<>();
		if (severity == null || !severity.requiresExtendedFields()) {
			return missing;
		}
		if (isBlank(rootCause)) {
			missing.add("rootCause");
		}
		if (isBlank(witnesses)) {
			missing.add("witnesses");
		}
		if (isBlank(medicalTreatment)) {
			missing.add("medicalTreatment");
		}
		if (authorityNotified == null) {
			missing.add("authorityNotified");
		}
		return missing;
	}

	static List<String> missingSeverityFields(Incident incident) {
		return missingSeverityFields(incident.getSeverity(), incident.getRootCause(), incident.getWitnesses(),
				incident.getMedicalTreatment(), incident.getAuthorityNotified());
	}

	/** Same contract as for daily logs: the form's version must be the current one, else 409. */
	static void requireVersion(Incident incident, Long expectedVersion) {
		if (!Objects.equals(incident.getVersion(), expectedVersion)) {
			throw new ObjectOptimisticLockingFailureException(Incident.class, incident.getId());
		}
	}

	static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	static String blankToNull(String value) {
		return isBlank(value) ? null : value.strip();
	}

}
