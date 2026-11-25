package hr.algebra.sitelog.incident.service;

import java.util.EnumSet;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.incident.domain.Severity;
import hr.algebra.sitelog.incident.repository.CorrectiveActionRepository;
import hr.algebra.sitelog.incident.repository.IncidentRepository;
import hr.algebra.sitelog.user.service.CurrentUserProvider;

/**
 * Record-level rules of the incident workflow, used in {@code @PreAuthorize} as
 * {@code @incidentAccess} (and in templates). The role is checked in the expression; this bean
 * only answers questions about the record:
 * <ul>
 * <li>{@link #isOnOwnSite} - the incident is on a site the signed-in SITE_MANAGER manages;</li>
 * <li>{@link #isLowSeverityOnOwnSite} - the same, and the incident is LOW or MEDIUM: a site
 * manager handles those himself, HIGH / CRITICAL need the SAFETY_SUPERVISOR (AR2);</li>
 * <li>{@link #isAssignee} - the signed-in user is the assignee of the corrective action.</li>
 * </ul>
 * As in {@code SiteAccess}, an id that does not exist answers {@code true}, so the service
 * reports the normal 404 instead of a misleading 403.
 */
@Component("incidentAccess")
@Transactional(readOnly = true)
public class IncidentAccess {

	static final EnumSet<Severity> LOW_SEVERITIES = EnumSet.of(Severity.LOW, Severity.MEDIUM);

	private final IncidentRepository incidents;

	private final CorrectiveActionRepository actions;

	private final CurrentUserProvider currentUser;

	public IncidentAccess(IncidentRepository incidents, CorrectiveActionRepository actions,
			CurrentUserProvider currentUser) {
		this.incidents = incidents;
		this.actions = actions;
		this.currentUser = currentUser;
	}

	public boolean isOnOwnSite(Long incidentId) {
		if (incidentId == null) {
			return false;
		}
		return incidents.existsByIdAndSite_Manager_Id(incidentId, currentUser.currentUserId())
				|| !incidents.existsById(incidentId);
	}

	public boolean isLowSeverityOnOwnSite(Long incidentId) {
		if (incidentId == null) {
			return false;
		}
		return incidents.existsByIdAndSite_Manager_IdAndSeverityIn(incidentId, currentUser.currentUserId(),
				LOW_SEVERITIES) || !incidents.existsById(incidentId);
	}

	public boolean isAssignee(Long actionId) {
		if (actionId == null) {
			return false;
		}
		return actions.existsByIdAndAssignee_Id(actionId, currentUser.currentUserId()) || !actions.existsById(actionId);
	}

}
