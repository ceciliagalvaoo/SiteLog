package hr.algebra.sitelog.incident.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.audit.aspect.AuditedAction;
import hr.algebra.sitelog.audit.domain.DomainActionType;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.incident.domain.CorrectiveAction;
import hr.algebra.sitelog.incident.domain.CorrectiveActionStatus;
import hr.algebra.sitelog.incident.domain.Incident;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.repository.IncidentRepository;
import hr.algebra.sitelog.user.domain.AppUser;
import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.repository.AppUserRepository;
import hr.algebra.sitelog.user.service.UserOption;

/**
 * Corrective actions of an incident (child of the Incident aggregate).
 *
 * <ul>
 * <li><b>Who</b> - the SAFETY_SUPERVISOR creates, changes, assigns and deletes them on any
 * site; "mark as done" is also allowed to the action's own assignee
 * ({@code @incidentAccess.isAssignee}).</li>
 * <li><b>Rules</b> - not on a CLOSED incident; the due date cannot be in the past (an unchanged
 * past date of an overdue action may stay); the assignee must be an active site manager or
 * safety supervisor; a DONE action is final (no edit, no delete).</li>
 * <li><b>Status</b> - OPEN -> DONE (completed_at set, {@code ck_corrective_action_completed});
 * OPEN -> OVERDUE is the nightly job (M4); moving the due date of an OVERDUE action to today or
 * later makes it OPEN again.</li>
 * <li><b>Concurrency</b> - the incident is the aggregate root: it is loaded with
 * {@code OPTIMISTIC_FORCE_INCREMENT}, so every change of an action bumps the incident's version
 * and two concurrent changes conflict (409), exactly like work entries of a daily log.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class CorrectiveActionService {

	public static final String INCIDENT_CLOSED = "correctiveAction.flash.incidentClosed";

	public static final String ACTION_DONE = "correctiveAction.flash.done.final";

	public static final String DUE_DATE_PAST = "validation.correctiveAction.dueDate.past";

	public static final String ASSIGNEE_INVALID = "validation.correctiveAction.assignee.invalid";

	/** Roles that can be responsible for a corrective action. */
	static final EnumSet<RoleName> ASSIGNEE_ROLES = EnumSet.of(RoleName.SITE_MANAGER, RoleName.SAFETY_SUPERVISOR);

	private final IncidentRepository incidents;

	private final AppUserRepository users;

	private final Clock clock;

	public CorrectiveActionService(IncidentRepository incidents, AppUserRepository users, Clock clock) {
		this.incidents = incidents;
		this.users = users;
		this.clock = clock;
	}

	/** Header of the form: the incident and the version the form sends back. */
	@PreAuthorize("hasRole('SAFETY_SUPERVISOR')")
	public IncidentHeader incidentForActions(Long incidentId) {
		Incident incident = incidents.findDetailById(incidentId)
			.orElseThrow(() -> new NotFoundException(Incident.class, incidentId));
		requireNotClosed(incident);
		return IncidentHeader.of(incident);
	}

	/** One action for the edit form. */
	@PreAuthorize("hasRole('SAFETY_SUPERVISOR')")
	public CorrectiveActionView get(Long incidentId, Long actionId) {
		Incident incident = incidents.findDetailById(incidentId)
			.orElseThrow(() -> new NotFoundException(Incident.class, incidentId));
		CorrectiveAction action = actionOf(incident, actionId);
		requireNotDone(action);
		return CorrectiveActionView.of(action, LocalDate.now(clock), clock.getZone());
	}

	/** Active site managers and safety supervisors, by name, for the assignee select. */
	public List<UserOption> assigneeOptions() {
		return users.findByRole_NameInAndActiveTrueOrderByFullNameAsc(ASSIGNEE_ROLES).stream()
			.map(user -> new UserOption(user.getId(), user.getFullName(), user.getEmail()))
			.toList();
	}

	@PreAuthorize("hasRole('SAFETY_SUPERVISOR')")
	@AuditedAction(DomainActionType.CORRECTIVE_ACTION_ADDED)
	@Transactional
	public Long create(Long incidentId, Long incidentVersion, CorrectiveActionCommand command) {
		Incident incident = loadForChange(incidentId, incidentVersion);
		requireFutureOrToday(command.dueDate());
		CorrectiveAction action = new CorrectiveAction(command.description().strip(), command.dueDate());
		action.setAssignee(assignee(command.assigneeId(), null));
		incident.addCorrectiveAction(action);
		incidents.flush();
		return action.getId();
	}

	@PreAuthorize("hasRole('SAFETY_SUPERVISOR')")
	@AuditedAction(DomainActionType.CORRECTIVE_ACTION_UPDATED)
	@Transactional
	public void update(Long incidentId, Long actionId, Long incidentVersion, CorrectiveActionCommand command) {
		Incident incident = loadForChange(incidentId, incidentVersion);
		CorrectiveAction action = actionOf(incident, actionId);
		requireNotDone(action);
		if (!Objects.equals(action.getDueDate(), command.dueDate())) {
			requireFutureOrToday(command.dueDate());
		}
		action.setDescription(command.description().strip());
		action.setDueDate(command.dueDate());
		action.setAssignee(assignee(command.assigneeId(), action.getAssignee()));
		if (action.getStatus() == CorrectiveActionStatus.OVERDUE && !command.dueDate().isBefore(LocalDate.now(clock))) {
			action.setStatus(CorrectiveActionStatus.OPEN);
		}
	}

	/** OPEN / OVERDUE -> DONE, by a supervisor or by the assignee. */
	@PreAuthorize("hasRole('SAFETY_SUPERVISOR') or @incidentAccess.isAssignee(#actionId)")
	@AuditedAction(DomainActionType.CORRECTIVE_ACTION_DONE)
	@Transactional
	public void markDone(Long incidentId, Long actionId, Long incidentVersion) {
		Incident incident = loadForChange(incidentId, incidentVersion);
		CorrectiveAction action = actionOf(incident, actionId);
		requireNotDone(action);
		action.setStatus(CorrectiveActionStatus.DONE);
		action.setCompletedAt(Instant.now(clock));
	}

	@PreAuthorize("hasRole('SAFETY_SUPERVISOR')")
	@AuditedAction(DomainActionType.CORRECTIVE_ACTION_DELETED)
	@Transactional
	public void delete(Long incidentId, Long actionId, Long incidentVersion) {
		Incident incident = loadForChange(incidentId, incidentVersion);
		CorrectiveAction action = actionOf(incident, actionId);
		requireNotDone(action);
		incident.removeCorrectiveAction(action);
	}

	/**
	 * 1) registers OPTIMISTIC_FORCE_INCREMENT on the incident (version + 1 at commit even though
	 * only a child changes); 2) the same managed instance with its actions initialised.
	 */
	private Incident loadForChange(Long incidentId, Long incidentVersion) {
		Incident incident = incidents.findForChildUpdateById(incidentId)
			.orElseThrow(() -> new NotFoundException(Incident.class, incidentId));
		incidents.findDetailById(incidentId);
		IncidentRules.requireVersion(incident, incidentVersion);
		requireNotClosed(incident);
		return incident;
	}

	private static CorrectiveAction actionOf(Incident incident, Long actionId) {
		return incident.getCorrectiveActions().stream()
			.filter(action -> action.getId().equals(actionId))
			.findFirst()
			.orElseThrow(() -> new NotFoundException(CorrectiveAction.class, actionId));
	}

	private static void requireNotClosed(Incident incident) {
		if (incident.getStatus() == IncidentStatus.CLOSED) {
			throw new InvalidTransitionException(INCIDENT_CLOSED, "incident " + incident.getId() + " is CLOSED");
		}
	}

	private static void requireNotDone(CorrectiveAction action) {
		if (action.getStatus() == CorrectiveActionStatus.DONE) {
			throw new InvalidTransitionException(ACTION_DONE, "corrective_action " + action.getId() + " is DONE");
		}
	}

	private void requireFutureOrToday(LocalDate dueDate) {
		if (dueDate.isBefore(LocalDate.now(clock))) {
			throw new InvalidFieldException("dueDate", DUE_DATE_PAST);
		}
	}

	/** The new assignee: must be active and in an assignee role, unless it is the unchanged current one. */
	private AppUser assignee(Long assigneeId, AppUser current) {
		if (assigneeId == null) {
			return null;
		}
		if (current != null && assigneeId.equals(current.getId())) {
			return current;
		}
		return users.findWithRoleById(assigneeId)
			.filter(user -> user.isActive() && ASSIGNEE_ROLES.contains(user.getRole().getName()))
			.orElseThrow(() -> new InvalidFieldException("assigneeId", ASSIGNEE_INVALID));
	}

}
