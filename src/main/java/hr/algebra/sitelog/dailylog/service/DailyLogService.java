package hr.algebra.sitelog.dailylog.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.repository.DailyLogRepository;
import hr.algebra.sitelog.dailylog.repository.DailyLogSummary;
import hr.algebra.sitelog.dailylog.repository.LogReopenRequestRepository;
import hr.algebra.sitelog.incident.repository.IncidentRepository;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.site.repository.SiteRepository;
import hr.algebra.sitelog.user.repository.AppUserRepository;
import hr.algebra.sitelog.user.service.CurrentUserProvider;

/**
 * The DailyLog aggregate as a whole: list, detail, create / update / delete of the log with its
 * nested work entries and crew rows (one form, as in the prototype), and the sign-off.
 *
 * <p>Rules:
 * <ul>
 * <li><b>AR1 part 1 - lock after sign-off.</b> Every write checks {@link LogStatus#isEditable()}
 * first and throws {@code LogLockedException} (409); the V3 trigger rejects the same writes in the
 * database. Reopen with reason and approval is milestone M3.</li>
 * <li><b>Optimistic locking.</b> The form sends the version it was loaded with; a different
 * current version, or a concurrent commit caught by {@code @Version}, ends as
 * {@code ObjectOptimisticLockingFailureException} (409). Because an update may change only
 * children, the root is loaded with {@code OPTIMISTIC_FORCE_INCREMENT} (ADR-0001 item 6).</li>
 * <li><b>Delete</b> (decided 07/11): only a DRAFT log with no incident and no reopen request;
 * its work entries and crew rows go with it (cascade, they are part of the aggregate).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class DailyLogService {

	public static final String DATE_DUPLICATE = "validation.dailyLog.date.duplicate";

	public static final String DATE_FUTURE = "validation.dailyLog.date.future";

	public static final String DELETE_NOT_DRAFT = "dailyLog.flash.deleteBlocked.status";

	public static final String DELETE_LINKED = "dailyLog.flash.deleteBlocked.linked";

	public static final String ALREADY_SIGNED_OFF = "dailyLog.flash.alreadySignedOff";

	public static final String SIGN_OFF_EMPTY = "dailyLog.flash.signOffEmpty";

	private final DailyLogRepository dailyLogs;

	private final SiteRepository sites;

	private final IncidentRepository incidents;

	private final LogReopenRequestRepository reopenRequests;

	private final AppUserRepository users;

	private final CurrentUserProvider currentUser;

	private final DailyLogAssembler assembler;

	private final Clock clock;

	public DailyLogService(DailyLogRepository dailyLogs, SiteRepository sites, IncidentRepository incidents,
			LogReopenRequestRepository reopenRequests, AppUserRepository users, CurrentUserProvider currentUser,
			DailyLogAssembler assembler, Clock clock) {
		this.dailyLogs = dailyLogs;
		this.sites = sites;
		this.incidents = incidents;
		this.reopenRequests = reopenRequests;
		this.users = users;
		this.currentUser = currentUser;
		this.assembler = assembler;
		this.clock = clock;
	}

	/**
	 * A site's log list with work entry count, crew count and crew hours per row, computed by one
	 * aggregate query (the N+1 fix, see {@code docs/performance.md}).
	 */
	public Page<DailyLogSummary> listForSite(Long siteId, Pageable pageable) {
		return dailyLogs.findSummariesBySiteId(siteId, pageable);
	}

	/** Detail page: the whole aggregate in one SQL statement ({@code findDetailById}). */
	public DailyLogDetails getDetail(Long id) {
		DailyLog log = dailyLogs.findDetailById(id).orElseThrow(() -> new NotFoundException(DailyLog.class, id));
		return toDetails(log, isDeletable(log));
	}

	/** Same data as the detail, refused for a locked log (the edit form is not offered). */
	public DailyLogDetails getForEdit(Long id) {
		DailyLog log = dailyLogs.findDetailById(id).orElseThrow(() -> new NotFoundException(DailyLog.class, id));
		DailyLogRules.requireEditable(log);
		return toDetails(log, isDeletable(log));
	}

	/** Header of the child pages (work entry / crew assignment forms). */
	public DailyLogHeader getHeader(Long id) {
		return dailyLogs.findWithSiteById(id).map(DailyLogHeader::of)
			.orElseThrow(() -> new NotFoundException(DailyLog.class, id));
	}

	/** Header for a page that changes a child (new / edit work entry or crew row): refused when locked. */
	public DailyLogHeader getHeaderForChange(Long id) {
		DailyLog log = dailyLogs.findWithSiteById(id).orElseThrow(() -> new NotFoundException(DailyLog.class, id));
		DailyLogRules.requireEditable(log);
		return DailyLogHeader.of(log);
	}

	@Transactional
	public Long create(Long siteId, DailyLogCommand command) {
		Site site = sites.findById(siteId).orElseThrow(() -> new NotFoundException(Site.class, siteId));
		checkDate(command.logDate());
		if (dailyLogs.existsBySite_IdAndLogDate(siteId, command.logDate())) {
			throw new InvalidFieldException("logDate", DATE_DUPLICATE);
		}
		DailyLog log = new DailyLog(site, command.logDate());
		log.setWeather(command.weather());
		log.setNotes(command.notes());
		assembler.syncWorkEntries(log, command.workEntries());
		return dailyLogs.save(log).getId();
	}

	@Transactional
	public void update(Long id, Long expectedVersion, DailyLogCommand command) {
		// 1) registers OPTIMISTIC_FORCE_INCREMENT on the root: version + 1 at commit even if only children change
		DailyLog log = dailyLogs.findForChildUpdateById(id).orElseThrow(() -> new NotFoundException(DailyLog.class, id));
		// 2) same managed instance, now with work entries, assignments and crews initialised
		dailyLogs.findDetailById(id);
		DailyLogRules.requireVersion(log, expectedVersion);
		DailyLogRules.requireEditable(log);
		checkDate(command.logDate());
		if (dailyLogs.existsBySite_IdAndLogDateAndIdNot(log.getSite().getId(), command.logDate(), id)) {
			throw new InvalidFieldException("logDate", DATE_DUPLICATE);
		}
		log.setLogDate(command.logDate());
		log.setWeather(command.weather());
		log.setNotes(command.notes());
		assembler.syncWorkEntries(log, command.workEntries());
	}

	/** Deletes a DRAFT log with its work entries and crew rows; returns the site id for the redirect. */
	@Transactional
	public Long delete(Long id, Long expectedVersion) {
		DailyLog log = dailyLogs.findDetailById(id).orElseThrow(() -> new NotFoundException(DailyLog.class, id));
		DailyLogRules.requireVersion(log, expectedVersion);
		DailyLogRules.requireEditable(log);
		if (log.getStatus() != LogStatus.DRAFT) {
			throw new DeletionNotAllowedException(DELETE_NOT_DRAFT, "daily_log " + id + " is " + log.getStatus());
		}
		if (incidents.existsByDailyLog_Id(id) || reopenRequests.existsByDailyLog_Id(id)) {
			throw new DeletionNotAllowedException(DELETE_LINKED, "daily_log " + id + " is referenced");
		}
		Long siteId = log.getSite().getId();
		dailyLogs.delete(log);
		return siteId;
	}

	/**
	 * Sign-off: DRAFT or REOPENED -> SIGNED_OFF, recording who and when. From then on the log is
	 * locked. Two concurrent sign-offs (or a sign-off racing an edit) both load version N; the
	 * first commit makes it N + 1, the second UPDATE ... WHERE version = N matches no row and
	 * fails with 409. Role checks ({@code @PreAuthorize}, own site) come in M3.
	 */
	@Transactional
	public void signOff(Long id, Long expectedVersion) {
		DailyLog log = dailyLogs.findDetailById(id).orElseThrow(() -> new NotFoundException(DailyLog.class, id));
		DailyLogRules.requireVersion(log, expectedVersion);
		if (log.getStatus() == LogStatus.SIGNED_OFF) {
			throw new InvalidTransitionException(ALREADY_SIGNED_OFF, "daily_log " + id + " is already SIGNED_OFF");
		}
		if (log.getWorkEntries().isEmpty()) {
			throw new InvalidTransitionException(SIGN_OFF_EMPTY, "daily_log " + id + " has no work entries");
		}
		log.setStatus(LogStatus.SIGNED_OFF);
		log.setSignedOffBy(users.getReferenceById(currentUser.currentUserId()));
		log.setSignedOffAt(Instant.now(clock));
	}

	/** A log records work that happened: its date cannot be after today (in the sites' zone). */
	private void checkDate(LocalDate logDate) {
		if (logDate.isAfter(LocalDate.now(clock))) {
			throw new InvalidFieldException("logDate", DATE_FUTURE);
		}
	}

	private boolean isDeletable(DailyLog log) {
		return log.getStatus() == LogStatus.DRAFT
				&& !incidents.existsByDailyLog_Id(log.getId())
				&& !reopenRequests.existsByDailyLog_Id(log.getId());
	}

	private DailyLogDetails toDetails(DailyLog log, boolean deletable) {
		ZonedDateTime signedOffAt = log.getSignedOffAt() == null ? null
				: log.getSignedOffAt().atZone(clock.getZone());
		return new DailyLogDetails(log.getId(), log.getSite().getId(), log.getSite().getCode(),
				log.getSite().getName(), log.getLogDate(), log.getStatus(), log.getRevision(), log.getWeather(),
				log.getNotes(), log.getSignedOffBy() == null ? null : log.getSignedOffBy().getFullName(),
				signedOffAt, log.getVersion(), log.getStatus().isEditable(), deletable,
				log.getWorkEntries().stream().map(WorkEntryView::of).toList());
	}

}
