package hr.algebra.sitelog.incident.service;

import static hr.algebra.sitelog.support.TestEntities.NOW;
import static hr.algebra.sitelog.support.TestEntities.withId;

import java.time.LocalDate;

import org.springframework.test.util.ReflectionTestUtils;

import hr.algebra.sitelog.incident.domain.CorrectiveAction;
import hr.algebra.sitelog.incident.domain.CorrectiveActionStatus;
import hr.algebra.sitelog.incident.domain.Incident;
import hr.algebra.sitelog.incident.domain.IncidentCategory;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.domain.Severity;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.user.domain.AppUser;

/** Incident entities for the unit tests of the incident services. */
final class IncidentFixtures {

	private IncidentFixtures() {
	}

	static Incident incident(Long id, Site site, Severity severity, IncidentStatus status, AppUser reporter,
			long version) {
		Incident incident = withId(new Incident(site, NOW.minusSeconds(7200), "Fall from slab edge",
				"A worker fell", severity, IncidentCategory.FALL_FROM_HEIGHT, reporter), id);
		incident.setStatus(status);
		ReflectionTestUtils.setField(incident, "version", version);
		return incident;
	}

	/** Fills the four AR2 fields required for HIGH / CRITICAL. */
	static Incident complete(Incident incident) {
		incident.setRootCause("Edge protection removed");
		incident.setWitnesses("Ivan Peric");
		incident.setMedicalTreatment("First aid");
		incident.setAuthorityNotified(true);
		return incident;
	}

	static CorrectiveAction action(Long id, Incident incident, CorrectiveActionStatus status, LocalDate due,
			AppUser assignee) {
		CorrectiveAction action = withId(new CorrectiveAction("Reinstall edge protection", due), id);
		action.setStatus(status);
		action.setAssignee(assignee);
		if (status == CorrectiveActionStatus.DONE) {
			action.setCompletedAt(NOW);
		}
		incident.addCorrectiveAction(action);
		return action;
	}

}
