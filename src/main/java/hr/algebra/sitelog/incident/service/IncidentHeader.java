package hr.algebra.sitelog.incident.service;

import hr.algebra.sitelog.incident.domain.Incident;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.domain.Severity;

/** Header of the corrective action form: which incident, and the version the form must send back. */
public record IncidentHeader(Long id, Long siteId, String siteCode, String siteName, String title, Severity severity,
		IncidentStatus status, Long version) {

	static IncidentHeader of(Incident incident) {
		return new IncidentHeader(incident.getId(), incident.getSite().getId(), incident.getSite().getCode(),
				incident.getSite().getName(), incident.getTitle(), incident.getSeverity(), incident.getStatus(),
				incident.getVersion());
	}

}
