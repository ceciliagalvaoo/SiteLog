package hr.algebra.sitelog.incident.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.audit.aspect.AuditedAction;
import hr.algebra.sitelog.audit.aspect.AuditedAction.IdSource;
import hr.algebra.sitelog.audit.domain.AuditedEntityType;
import hr.algebra.sitelog.audit.domain.DomainActionType;
import hr.algebra.sitelog.audit.service.DomainActionService;
import hr.algebra.sitelog.audit.service.DomainActionView;
import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.exception.MissingSeverityFieldsException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.crew.domain.Crew;
import hr.algebra.sitelog.crew.repository.CrewRepository;
import hr.algebra.sitelog.crew.service.CrewOption;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.repository.DailyLogRepository;
import hr.algebra.sitelog.incident.domain.CorrectiveActionStatus;
import hr.algebra.sitelog.incident.domain.Incident;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.domain.Severity;
import hr.algebra.sitelog.incident.repository.IncidentRepository;
import hr.algebra.sitelog.notification.domain.NotificationType;
import hr.algebra.sitelog.notification.service.NotificationPublisher;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.site.repository.SiteRepository;
import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.repository.AppUserRepository;
import hr.algebra.sitelog.user.service.CurrentUserProvider;

/**
 * Incidents and the AR2 severity workflow:
 * {@code REPORTED -> UNDER_REVIEW -> SIGNED_OFF -> CLOSED}.
 *
 * <ul>
 * <li><b>Report</b> - the SITE_MANAGER of the site ({@code @siteAccess}); optional link to a
 * daily log of the same site; affected crews must be active. A HIGH / CRITICAL report notifies
 * every active safety supervisor (HIGH_SEVERITY_INCIDENT).</li>
 * <li><b>Required fields by severity</b> - HIGH / CRITICAL need root cause, witnesses, medical
 * treatment and authority notified, checked on report, on every change and again at sign-off
 * ({@link MissingSeverityFieldsException}, 422). The form checks the same with a validation
 * group (UX); this service is the rule for every caller; {@code ck_incident_severity_fields}
 * is the database safety net.</li>
 * <li><b>Who handles it</b> - start review, sign-off and close: the SAFETY_SUPERVISOR on any
 * site; the site manager only for LOW / MEDIUM incidents of his or her own site
 * ({@code @incidentAccess.isLowSeverityOnOwnSite}). So a HIGH / CRITICAL incident is always
 * signed off by a supervisor before it can be closed. Only a supervisor may lower a HIGH /
 * CRITICAL severity (otherwise a manager could downgrade and sign it himself).</li>
 * <li><b>Edit</b> - while REPORTED or UNDER_REVIEW; a site manager only while REPORTED (once
 * the review started, the supervisor owns the record).</li>
 * <li><b>Close</b> - only when every corrective action is DONE.</li>
 * <li><b>Delete</b> - only a REPORTED incident without corrective actions, by its site's manager.</li>
 * <li><b>Concurrency</b> - every change sends the version shown on the page; a stale one or a
 * concurrent commit ({@code @Version}) is a 409.</li>
 * </ul>
 * Every workflow method is an {@code @AuditedAction}; the detail page shows them as timeline.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class IncidentService {

	public static final String NOT_EDITABLE = "incident.flash.notEditable";

	public static final String UNDER_REVIEW_BY_SUPERVISOR = "incident.flash.underReview";

	public static final String REVIEW_NOT_REPORTED = "incident.flash.review.notReported";

	public static final String SIGN_OFF_NOT_UNDER_REVIEW = "incident.flash.signOff.notUnderReview";

	public static final String CLOSE_NOT_SIGNED_OFF = "incident.flash.close.notSignedOff";

	public static final String CLOSE_OPEN_ACTIONS = "incident.flash.close.openActions";

	public static final String DELETE_BLOCKED = "incident.flash.deleteBlocked";

	public static final String OCCURRED_FUTURE = "validation.incident.occurredAt.future";

	public static final String DAILY_LOG_INVALID = "validation.incident.dailyLog.invalid";

	public static final String CREWS_INVALID = "validation.incident.crews.invalid";

	public static final String SEVERITY_DOWNGRADE = "validation.incident.severity.downgrade";

	private static final EnumSet<CorrectiveActionStatus> OPEN_ACTIONS = EnumSet.of(CorrectiveActionStatus.OPEN,
			CorrectiveActionStatus.OVERDUE);

	private final IncidentRepository incidents;

	private final SiteRepository sites;

	private final DailyLogRepository dailyLogs;

	private final CrewRepository crews;

	private final AppUserRepository users;

	private final CurrentUserProvider currentUser;

	private final NotificationPublisher notifications;

	private final DomainActionService domainActions;

	private final Clock clock;

	public IncidentService(IncidentRepository incidents, SiteRepository sites, DailyLogRepository dailyLogs,
			CrewRepository crews, AppUserRepository users, CurrentUserProvider currentUser,
			NotificationPublisher notifications, DomainActionService domainActions, Clock clock) {
		this.incidents = incidents;
		this.sites = sites;
		this.dailyLogs = dailyLogs;
		this.crews = crews;
		this.users = users;
		this.currentUser = currentUser;
		this.notifications = notifications;
		this.domainActions = domainActions;
		this.clock = clock;
	}

	// ------------------------------------------------------------------ reads (every signed-in user)

	/** Incident list with optional site / status / severity filters, one query plus the count. */
	public Page<IncidentListItem> list(IncidentFilter filter, Pageable pageable) {
		return incidents.findAll(specification(filter == null ? IncidentFilter.NONE : filter), pageable)
			.map(incident -> IncidentListItem.of(incident, clock.getZone()));
	}

	/** Detail: the whole incident in one SQL statement, plus its timeline (two small queries). */
	public IncidentDetails getDetail(Long id) {
		return toDetails(load(id));
	}

	/** Same data as the detail for the edit form; refused when the caller may not edit it now. */
	@PreAuthorize("hasRole('SAFETY_SUPERVISOR') or (hasRole('SITE_MANAGER') and @incidentAccess.isOnOwnSite(#id))")
	public IncidentDetails getForEdit(Long id) {
		Incident incident = load(id);
		requireEditableByCaller(incident);
		return toDetails(incident);
	}

	/**
	 * Daily logs of the site that a new report can be linked to, newest first. Also the access
	 * check of the "report incident" form: only the site's own manager gets it.
	 */
	@PreAuthorize("hasRole('SITE_MANAGER') and @siteAccess.canManageSite(#siteId)")
	public List<DailyLogOption> dailyLogOptionsForReport(Long siteId) {
		if (!sites.existsById(siteId)) {
			throw new NotFoundException(Site.class, siteId);
		}
		return dailyLogOptions(siteId);
	}

	/** Daily logs of the site for the edit form (any caller allowed to edit). */
	public List<DailyLogOption> dailyLogOptions(Long siteId) {
		return dailyLogs.findTop60BySite_IdOrderByLogDateDesc(siteId).stream()
			.map(log -> new DailyLogOption(log.getId(), log.getLogDate(), log.getStatus()))
			.toList();
	}

	// ------------------------------------------------------------------ writes

	/** Reports an incident on the manager's own site; returns its id. */
	@PreAuthorize("hasRole('SITE_MANAGER') and @siteAccess.canManageSite(#siteId)")
	@AuditedAction(value = DomainActionType.INCIDENT_REPORTED, id = IdSource.RETURN_VALUE)
	@Transactional
	public Long report(Long siteId, IncidentCommand command) {
		Site site = sites.findById(siteId).orElseThrow(() -> new NotFoundException(Site.class, siteId));
		Incident incident = new Incident(site, command.occurredAt(), command.title(), command.description(),
				command.severity(), command.category(), users.getReferenceById(currentUser.currentUserId()));
		apply(incident, command);
		requireSeverityFields(incident);
		Incident saved = incidents.save(incident);
		if (saved.getSeverity().requiresExtendedFields()) {
			notifyHighSeverity(saved);
		}
		return saved.getId();
	}

	/** Changes the report (fields, severity, links). Raising it to HIGH / CRITICAL notifies the supervisors. */
	@PreAuthorize("hasRole('SAFETY_SUPERVISOR') or (hasRole('SITE_MANAGER') and @incidentAccess.isOnOwnSite(#id))")
	@AuditedAction(DomainActionType.INCIDENT_UPDATED)
	@Transactional
	public void update(Long id, Long expectedVersion, IncidentCommand command) {
		Incident incident = load(id);
		IncidentRules.requireVersion(incident, expectedVersion);
		requireEditableByCaller(incident);
		Severity before = incident.getSeverity();
		if (before.requiresExtendedFields() && !command.severity().requiresExtendedFields() && !isSupervisor()) {
			throw new InvalidFieldException("severity", SEVERITY_DOWNGRADE);
		}
		incident.setTitle(command.title());
		incident.setDescription(command.description());
		incident.setSeverity(command.severity());
		incident.setCategory(command.category());
		incident.setOccurredAt(command.occurredAt());
		apply(incident, command);
		requireSeverityFields(incident);
		if (!before.requiresExtendedFields() && incident.getSeverity().requiresExtendedFields()) {
			notifyHighSeverity(incident);
		}
	}

	/** REPORTED -> UNDER_REVIEW. */
	@PreAuthorize("hasRole('SAFETY_SUPERVISOR') or (hasRole('SITE_MANAGER') and @incidentAccess.isLowSeverityOnOwnSite(#id))")
	@AuditedAction(DomainActionType.INCIDENT_REVIEW_STARTED)
	@Transactional
	public void startReview(Long id, Long expectedVersion) {
		Incident incident = loadForTransition(id, expectedVersion);
		if (incident.getStatus() != IncidentStatus.REPORTED) {
			throw new InvalidTransitionException(REVIEW_NOT_REPORTED, "incident " + id + " is " + incident.getStatus());
		}
		incident.setStatus(IncidentStatus.UNDER_REVIEW);
	}

	/**
	 * UNDER_REVIEW -> SIGNED_OFF, recording who and when. HIGH / CRITICAL: only a SAFETY_SUPERVISOR
	 * ({@code @PreAuthorize}, checked again here against the loaded severity) and only with every
	 * extra field filled (422 otherwise).
	 */
	@PreAuthorize("hasRole('SAFETY_SUPERVISOR') or (hasRole('SITE_MANAGER') and @incidentAccess.isLowSeverityOnOwnSite(#id))")
	@AuditedAction(DomainActionType.INCIDENT_SIGNED_OFF)
	@Transactional
	public void signOff(Long id, Long expectedVersion) {
		Incident incident = loadForTransition(id, expectedVersion);
		if (incident.getStatus() != IncidentStatus.UNDER_REVIEW) {
			throw new InvalidTransitionException(SIGN_OFF_NOT_UNDER_REVIEW,
					"incident " + id + " is " + incident.getStatus());
		}
		requireSeverityFields(incident);
		incident.setStatus(IncidentStatus.SIGNED_OFF);
		incident.setSignedOffBy(users.getReferenceById(currentUser.currentUserId()));
		incident.setSignedOffAt(Instant.now(clock));
	}

	/** SIGNED_OFF -> CLOSED, only when every corrective action is DONE. */
	@PreAuthorize("hasRole('SAFETY_SUPERVISOR') or (hasRole('SITE_MANAGER') and @incidentAccess.isLowSeverityOnOwnSite(#id))")
	@AuditedAction(DomainActionType.INCIDENT_CLOSED)
	@Transactional
	public void close(Long id, Long expectedVersion) {
		Incident incident = loadForTransition(id, expectedVersion);
		if (incident.getStatus() != IncidentStatus.SIGNED_OFF) {
			throw new InvalidTransitionException(CLOSE_NOT_SIGNED_OFF, "incident " + id + " is " + incident.getStatus());
		}
		if (incident.getCorrectiveActions().stream().anyMatch(action -> OPEN_ACTIONS.contains(action.getStatus()))) {
			throw new InvalidTransitionException(CLOSE_OPEN_ACTIONS, "incident " + id + " has open corrective actions");
		}
		incident.setStatus(IncidentStatus.CLOSED);
	}

	/** Deletes a REPORTED incident without corrective actions; returns its site id for the redirect. */
	@PreAuthorize("hasRole('SITE_MANAGER') and @incidentAccess.isOnOwnSite(#id)")
	@AuditedAction(DomainActionType.INCIDENT_DELETED)
	@Transactional
	public Long delete(Long id, Long expectedVersion) {
		Incident incident = load(id);
		IncidentRules.requireVersion(incident, expectedVersion);
		if (incident.getStatus() != IncidentStatus.REPORTED || !incident.getCorrectiveActions().isEmpty()) {
			throw new DeletionNotAllowedException(DELETE_BLOCKED, "incident " + id + " is " + incident.getStatus());
		}
		Long siteId = incident.getSite().getId();
		incidents.delete(incident);
		return siteId;
	}

	// ------------------------------------------------------------------ helpers

	private Incident load(Long id) {
		return incidents.findDetailById(id).orElseThrow(() -> new NotFoundException(Incident.class, id));
	}

	/**
	 * Loads for a workflow step and repeats the AR2 rule on the loaded record: whatever the
	 * expression saw, a HIGH / CRITICAL incident is never moved by someone who is not a supervisor.
	 */
	private Incident loadForTransition(Long id, Long expectedVersion) {
		Incident incident = load(id);
		IncidentRules.requireVersion(incident, expectedVersion);
		if (incident.getSeverity().requiresExtendedFields() && !isSupervisor()) {
			throw new AccessDeniedException("HIGH/CRITICAL incident " + id + " needs a safety supervisor");
		}
		return incident;
	}

	private void requireEditableByCaller(Incident incident) {
		if (!incident.getStatus().isOpen()) {
			throw new InvalidTransitionException(NOT_EDITABLE,
					"incident " + incident.getId() + " is " + incident.getStatus());
		}
		if (incident.getStatus() == IncidentStatus.UNDER_REVIEW && !isSupervisor()) {
			throw new InvalidTransitionException(UNDER_REVIEW_BY_SUPERVISOR,
					"incident " + incident.getId() + " is under review");
		}
	}

	private boolean isSupervisor() {
		return currentUser.currentUser().hasRole(RoleName.SAFETY_SUPERVISOR);
	}

	/** Fields shared by report and update: date check, daily log link, crews, AR2 fields. */
	private void apply(Incident incident, IncidentCommand command) {
		if (command.occurredAt() != null && command.occurredAt().isAfter(Instant.now(clock))) {
			throw new InvalidFieldException("occurredAt", OCCURRED_FUTURE);
		}
		incident.setDailyLog(dailyLogFor(incident.getSite().getId(), command.dailyLogId()));
		syncCrews(incident, command.affectedCrewIds());
		incident.setRootCause(IncidentRules.blankToNull(command.rootCause()));
		incident.setWitnesses(IncidentRules.blankToNull(command.witnesses()));
		incident.setMedicalTreatment(IncidentRules.blankToNull(command.medicalTreatment()));
		incident.setAuthorityNotified(command.authorityNotified());
	}

	private DailyLog dailyLogFor(Long siteId, Long dailyLogId) {
		if (dailyLogId == null) {
			return null;
		}
		if (!dailyLogs.existsByIdAndSite_Id(dailyLogId, siteId)) {
			throw new InvalidFieldException("dailyLogId", DAILY_LOG_INVALID);
		}
		return dailyLogs.getReferenceById(dailyLogId);
	}

	/** New crews must exist and be active; a crew already listed stays even if deactivated since. */
	private void syncCrews(Incident incident, List<Long> crewIds) {
		Set<Long> wanted = new LinkedHashSet<>(crewIds);
		Map<Long, Crew> current = incident.getAffectedCrews().stream()
			.collect(Collectors.toMap(Crew::getId, Function.identity()));
		List<Long> added = wanted.stream().filter(crewId -> !current.containsKey(crewId)).toList();
		List<Crew> found = added.isEmpty() ? List.of() : crews.findAllById(added);
		if (found.size() != added.size() || found.stream().anyMatch(crew -> !crew.isActive())) {
			throw new InvalidFieldException("affectedCrewIds", CREWS_INVALID);
		}
		new ArrayList<>(current.values()).stream()
			.filter(crew -> !wanted.contains(crew.getId()))
			.forEach(incident::removeAffectedCrew);
		found.forEach(incident::addAffectedCrew);
	}

	private static void requireSeverityFields(Incident incident) {
		List<String> missing = IncidentRules.missingSeverityFields(incident);
		if (!missing.isEmpty()) {
			throw new MissingSeverityFieldsException(incident.getId(), missing);
		}
	}

	private void notifyHighSeverity(Incident incident) {
		notifications.notifyRole(RoleName.SAFETY_SUPERVISOR, NotificationType.HIGH_SEVERITY_INCIDENT,
				"/incidents/" + incident.getId(), incident.getSite().getCode(), incident.getSeverity().name(),
				incident.getTitle());
	}

	private static Specification<Incident> specification(IncidentFilter filter) {
		Specification<Incident> spec = (root, query, cb) -> cb.conjunction();
		if (filter.siteId() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("site").get("id"), filter.siteId()));
		}
		if (filter.status() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
		}
		if (filter.severity() != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("severity"), filter.severity()));
		}
		return spec;
	}

	private IncidentDetails toDetails(Incident incident) {
		ZoneId zone = clock.getZone();
		LocalDate today = LocalDate.now(clock);
		return new IncidentDetails(incident.getId(), incident.getSite().getId(), incident.getSite().getCode(),
				incident.getSite().getName(), incident.getDailyLog() == null ? null : incident.getDailyLog().getId(),
				incident.getDailyLog() == null ? null : incident.getDailyLog().getLogDate(),
				incident.getOccurredAt().atZone(zone), incident.getTitle(), incident.getDescription(),
				incident.getSeverity(), incident.getCategory(), incident.getStatus(), incident.getRootCause(),
				incident.getWitnesses(), incident.getMedicalTreatment(), incident.getAuthorityNotified(),
				incident.getReportedBy().getFullName(),
				incident.getSignedOffBy() == null ? null : incident.getSignedOffBy().getFullName(),
				incident.getSignedOffAt() == null ? null : incident.getSignedOffAt().atZone(zone),
				incident.getVersion(),
				incident.getAffectedCrews().stream()
					.map(crew -> new CrewOption(crew.getId(), crew.getName(), crew.getTrade(), crew.isActive()))
					.toList(),
				incident.getCorrectiveActions().stream().map(action -> CorrectiveActionView.of(action, today, zone))
					.toList(),
				IncidentRules.missingSeverityFields(incident), timeline(incident));
	}

	/**
	 * Recorded actions, oldest first. Incidents created before the aspect existed (the V4 demo
	 * data) have no recorded "reported" / "signed off" action; those two are then taken from the
	 * incident's own columns, so every timeline starts with the report.
	 */
	private List<DomainActionView> timeline(Incident incident) {
		ZoneId zone = clock.getZone();
		List<DomainActionView> recorded = domainActions.timeline(AuditedEntityType.INCIDENT, incident.getId());
		List<DomainActionView> timeline = new ArrayList<>();
		boolean reported = recorded.stream().anyMatch(a -> a.action() == DomainActionType.INCIDENT_REPORTED);
		boolean signed = recorded.stream().anyMatch(a -> a.action() == DomainActionType.INCIDENT_SIGNED_OFF);
		if (!reported) {
			timeline.add(DomainActionView.of(DomainActionType.INCIDENT_REPORTED, incident.getReportedBy().getFullName(),
					incident.getOccurredAt().atZone(zone)));
		}
		timeline.addAll(recorded);
		if (!signed && incident.getSignedOffAt() != null) {
			timeline.add(DomainActionView.of(DomainActionType.INCIDENT_SIGNED_OFF,
					incident.getSignedOffBy() == null ? null : incident.getSignedOffBy().getFullName(),
					incident.getSignedOffAt().atZone(zone)));
			timeline.sort(java.util.Comparator.comparing(DomainActionView::occurredAt));
		}
		return timeline;
	}

}
