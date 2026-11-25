package hr.algebra.sitelog.dailylog.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.audit.aspect.AuditedAction;
import hr.algebra.sitelog.audit.domain.DomainActionType;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.LogReopenRequest;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.domain.ReopenStatus;
import hr.algebra.sitelog.dailylog.repository.DailyLogRepository;
import hr.algebra.sitelog.dailylog.repository.LogReopenRequestRepository;
import hr.algebra.sitelog.notification.domain.NotificationType;
import hr.algebra.sitelog.notification.service.NotificationPublisher;
import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.repository.AppUserRepository;
import hr.algebra.sitelog.user.service.CurrentUser;
import hr.algebra.sitelog.user.service.CurrentUserProvider;

/**
 * AR1 part 2 - the audited reopen of a signed-off daily log:
 * {@code SIGNED_OFF -> (request with reason) -> supervisor approves -> REOPENED -> edits ->
 * sign-off again (DailyLogService#signOff) -> SIGNED_OFF}, revision + 1 per approved reopen.
 *
 * <ul>
 * <li><b>Request</b> - the SITE_MANAGER of the log's own site, only for a SIGNED_OFF log,
 * reason mandatory, at most one PENDING request per log (also {@code uk_log_reopen_request_pending}).
 * Every active safety supervisor gets a REOPEN_REQUESTED notification.</li>
 * <li><b>Approve</b> - SAFETY_SUPERVISOR: the request becomes APPROVED, the log REOPENED with
 * {@code revision + 1}. That is exactly the one change the V3 lock trigger accepts on a
 * SIGNED_OFF row (status -> REOPENED, revision + 1, content unchanged), and {@code @Version}
 * makes it conflict with any concurrent change of the log.</li>
 * <li><b>Reject</b> - SAFETY_SUPERVISOR, reason mandatory (V7 {@code decision_note}); the log
 * stays SIGNED_OFF and untouched.</li>
 * </ul>
 * Two supervisors deciding the same request at the same moment: the request row is read with
 * {@code SELECT ... FOR UPDATE}, so the second decision waits, then finds it decided (409).
 *
 * <p>Audit trail (ADR-0003): this table says why and who decided, the {@code @AuditedAction}
 * aspect records the action, Envers records what the reopen and the later edits changed.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class LogReopenService {

	public static final String NOT_SIGNED_OFF = "reopen.flash.notSignedOff";

	public static final String ALREADY_PENDING = "reopen.flash.alreadyPending";

	public static final String ALREADY_DECIDED = "reopen.flash.alreadyDecided";

	public static final String REASON_REQUIRED = "validation.dailyLog.reopenReason.required";

	public static final String REJECT_REASON_REQUIRED = "validation.reopen.rejectReason.required";

	private final LogReopenRequestRepository reopenRequests;

	private final DailyLogRepository dailyLogs;

	private final AppUserRepository users;

	private final CurrentUserProvider currentUser;

	private final NotificationPublisher notifications;

	private final Clock clock;

	public LogReopenService(LogReopenRequestRepository reopenRequests, DailyLogRepository dailyLogs,
			AppUserRepository users, CurrentUserProvider currentUser, NotificationPublisher notifications,
			Clock clock) {
		this.reopenRequests = reopenRequests;
		this.dailyLogs = dailyLogs;
		this.users = users;
		this.currentUser = currentUser;
		this.notifications = notifications;
		this.clock = clock;
	}

	/** Reopen part of the log detail: pending request and history (two small queries). */
	public ReopenPanel panel(Long dailyLogId) {
		DailyLog log = dailyLogs.findById(dailyLogId).orElseThrow(() -> new NotFoundException(DailyLog.class, dailyLogId));
		List<ReopenRequestView> history = history(dailyLogId);
		ReopenRequestView pending = history.stream().filter(ReopenRequestView::pending).findFirst().orElse(null);
		return new ReopenPanel(dailyLogId, log.getStatus(), pending, history,
				log.getStatus() == LogStatus.SIGNED_OFF && pending == null);
	}

	/** Every request of a log, newest first, with requester and decider. */
	public List<ReopenRequestView> history(Long dailyLogId) {
		return reopenRequests.findByDailyLog_IdOrderByRequestedAtDesc(dailyLogId).stream()
			.map(request -> ReopenRequestView.of(request, clock.getZone()))
			.toList();
	}

	/** The supervisor's queue: pending requests on all sites, oldest first (decided 24/11: supervisors only). */
	@PreAuthorize("hasRole('SAFETY_SUPERVISOR')")
	public List<PendingReopenItem> pendingRequests() {
		return reopenRequests.findByStatusOrderByRequestedAtAsc(ReopenStatus.PENDING).stream()
			.map(r -> new PendingReopenItem(r.getId(), r.getDailyLog().getId(), r.getDailyLog().getSite().getCode(),
					r.getDailyLog().getSite().getName(), r.getDailyLog().getLogDate(), r.getReason(),
					r.getRequestedBy().getFullName(), r.getRequestedAt().atZone(clock.getZone())))
			.toList();
	}

	/**
	 * The site manager asks to reopen a signed-off log; returns the request id.
	 * {@code expectedVersion} is the version shown on the page (the log must not have changed since).
	 */
	@PreAuthorize("hasRole('SITE_MANAGER') and @siteAccess.canManageLog(#dailyLogId)")
	@AuditedAction(DomainActionType.LOG_REOPEN_REQUESTED)
	@Transactional
	public Long request(Long dailyLogId, Long expectedVersion, String reason) {
		DailyLog log = dailyLogs.findWithSiteById(dailyLogId)
			.orElseThrow(() -> new NotFoundException(DailyLog.class, dailyLogId));
		DailyLogRules.requireVersion(log, expectedVersion);
		if (log.getStatus() != LogStatus.SIGNED_OFF) {
			throw new InvalidTransitionException(NOT_SIGNED_OFF, "daily_log " + dailyLogId + " is " + log.getStatus());
		}
		if (reopenRequests.existsByDailyLog_IdAndStatus(dailyLogId, ReopenStatus.PENDING)) {
			throw new InvalidTransitionException(ALREADY_PENDING, "daily_log " + dailyLogId + " has a pending request");
		}
		String text = requireText(reason, "reason", REASON_REQUIRED);
		CurrentUser requester = currentUser.currentUser();
		LogReopenRequest request = reopenRequests.save(new LogReopenRequest(log, text,
				users.getReferenceById(requester.id()), Instant.now(clock)));
		notifications.notifyRole(RoleName.SAFETY_SUPERVISOR, NotificationType.REOPEN_REQUESTED,
				"/daily-logs/" + dailyLogId, log.getSite().getCode(), log.getLogDate(), requester.fullName());
		return request.getId();
	}

	/**
	 * The supervisor approves: SIGNED_OFF -> REOPENED, revision + 1; {@code note} is optional.
	 * A concurrent change of the log ends in a version conflict (409) and nothing is decided.
	 */
	@PreAuthorize("hasRole('SAFETY_SUPERVISOR')")
	@AuditedAction(DomainActionType.LOG_REOPEN_APPROVED)
	@Transactional
	public void approve(Long dailyLogId, Long requestId, String note) {
		LogReopenRequest request = pendingRequest(dailyLogId, requestId);
		DailyLog log = dailyLogs.findById(dailyLogId).orElseThrow(() -> new NotFoundException(DailyLog.class, dailyLogId));
		if (log.getStatus() != LogStatus.SIGNED_OFF) {
			throw new InvalidTransitionException(NOT_SIGNED_OFF, "daily_log " + dailyLogId + " is " + log.getStatus());
		}
		// the only change the V3 lock trigger lets through on a SIGNED_OFF row
		log.setStatus(LogStatus.REOPENED);
		log.setRevision(log.getRevision() + 1);
		request.decide(ReopenStatus.APPROVED, users.getReferenceById(currentUser.currentUserId()), Instant.now(clock),
				blankToNull(note));
	}

	/** The supervisor rejects with a mandatory reason; the log stays SIGNED_OFF. */
	@PreAuthorize("hasRole('SAFETY_SUPERVISOR')")
	@AuditedAction(DomainActionType.LOG_REOPEN_REJECTED)
	@Transactional
	public void reject(Long dailyLogId, Long requestId, String reason) {
		String text = requireText(reason, "reason", REJECT_REASON_REQUIRED);
		LogReopenRequest request = pendingRequest(dailyLogId, requestId);
		request.decide(ReopenStatus.REJECTED, users.getReferenceById(currentUser.currentUserId()), Instant.now(clock),
				text);
	}

	/** The request, locked for the decision; it must belong to the log in the URL and still be PENDING. */
	private LogReopenRequest pendingRequest(Long dailyLogId, Long requestId) {
		LogReopenRequest request = reopenRequests.findForDecisionById(requestId)
			.filter(r -> r.getDailyLog().getId().equals(dailyLogId))
			.orElseThrow(() -> new NotFoundException(LogReopenRequest.class, requestId));
		if (!request.isPending()) {
			throw new InvalidTransitionException(ALREADY_DECIDED,
					"log_reopen_request " + requestId + " is already " + request.getStatus());
		}
		return request;
	}

	private static String requireText(String value, String field, String messageKey) {
		String text = blankToNull(value);
		if (text == null) {
			throw new InvalidFieldException(field, messageKey);
		}
		return text;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
