package hr.algebra.sitelog.incident.service;

import java.util.EnumSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.repository.IncidentRepository;

/**
 * Incident counters shown outside the incident screens (sidebar badge). The incident workflow
 * itself (AR2) is milestone M3.
 */
@Service
@Transactional(readOnly = true)
public class IncidentStatsService {

	/** Not yet signed off: still needs someone's attention. */
	public static final EnumSet<IncidentStatus> OPEN = EnumSet.of(IncidentStatus.REPORTED, IncidentStatus.UNDER_REVIEW);

	private final IncidentRepository incidents;

	public IncidentStatsService(IncidentRepository incidents) {
		this.incidents = incidents;
	}

	public long openIncidentCount() {
		return incidents.countByStatusIn(OPEN);
	}

}
