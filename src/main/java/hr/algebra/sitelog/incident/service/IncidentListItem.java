package hr.algebra.sitelog.incident.service;

import java.time.ZonedDateTime;

import hr.algebra.sitelog.incident.domain.Incident;
import hr.algebra.sitelog.incident.domain.IncidentCategory;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.domain.Severity;

/** One row of an incident list (all sites or one site). */
public record IncidentListItem(Long id, Long siteId, String siteCode, String siteName, ZonedDateTime occurredAt,
		String title, Severity severity, IncidentCategory category, IncidentStatus status, String reportedByName) {

	static IncidentListItem of(Incident incident, java.time.ZoneId zone) {
		return new IncidentListItem(incident.getId(), incident.getSite().getId(), incident.getSite().getCode(),
				incident.getSite().getName(), incident.getOccurredAt().atZone(zone), incident.getTitle(),
				incident.getSeverity(), incident.getCategory(), incident.getStatus(),
				incident.getReportedBy().getFullName());
	}

}
