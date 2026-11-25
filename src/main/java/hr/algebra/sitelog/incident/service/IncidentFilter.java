package hr.algebra.sitelog.incident.service;

import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.domain.Severity;

/** Optional filters of the incident list; null = no filter. */
public record IncidentFilter(Long siteId, IncidentStatus status, Severity severity) {

	public static final IncidentFilter NONE = new IncidentFilter(null, null, null);

	public static IncidentFilter forSite(Long siteId) {
		return new IncidentFilter(siteId, null, null);
	}

}
