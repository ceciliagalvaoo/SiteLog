package hr.algebra.sitelog.site.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.repository.DailyLogRepository;
import hr.algebra.sitelog.incident.domain.CorrectiveActionStatus;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.repository.CorrectiveActionRepository;
import hr.algebra.sitelog.incident.repository.IncidentRepository;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.site.repository.SiteRepository;
import hr.algebra.sitelog.user.domain.AppUser;
import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.repository.AppUserRepository;
import hr.algebra.sitelog.user.service.UserOption;

/**
 * Site management (Admin) and the site pages.
 *
 * <p>Delete rule (decided 07/11, ADR-0001 item 3): a site can only be deleted while it has no
 * daily logs, incidents or AI incident drafts. Otherwise it holds records and is finished by
 * setting an end date instead. The foreign keys ({@code ON DELETE RESTRICT}) refuse the delete
 * anyway; checking first gives the user a clear message instead of a constraint error.
 */
@Service
@Transactional(readOnly = true)
public class SiteService {

	public static final String CODE_DUPLICATE = "validation.site.code.duplicate";

	public static final String MANAGER_INVALID = "validation.site.manager.invalid";

	public static final String DELETE_BLOCKED = "site.flash.deleteBlocked";

	private static final EnumSet<LogStatus> AWAITING_SIGN_OFF = EnumSet.of(LogStatus.DRAFT, LogStatus.REOPENED);

	private static final EnumSet<IncidentStatus> OPEN_INCIDENT = EnumSet.of(IncidentStatus.REPORTED,
			IncidentStatus.UNDER_REVIEW);

	private static final EnumSet<CorrectiveActionStatus> OPEN_ACTION = EnumSet.of(CorrectiveActionStatus.OPEN,
			CorrectiveActionStatus.OVERDUE);

	private final SiteRepository sites;

	private final AppUserRepository users;

	private final DailyLogRepository dailyLogs;

	private final IncidentRepository incidents;

	private final CorrectiveActionRepository correctiveActions;

	private final Clock clock;

	public SiteService(SiteRepository sites, AppUserRepository users, DailyLogRepository dailyLogs,
			IncidentRepository incidents, CorrectiveActionRepository correctiveActions, Clock clock) {
		this.sites = sites;
		this.users = users;
		this.dailyLogs = dailyLogs;
		this.incidents = incidents;
		this.correctiveActions = correctiveActions;
		this.clock = clock;
	}

	/** Sites list with an optional text filter on code or name; the manager is fetched in the same query. */
	public Page<SiteListItem> list(String filter, Pageable pageable) {
		Page<Site> page = (filter == null || filter.isBlank())
				? sites.findAllWithManager(pageable)
				: sites.searchWithManager(filter.trim(), pageable);
		LocalDate today = LocalDate.now(clock);
		return page.map(site -> new SiteListItem(site.getId(), site.getCode(), site.getName(), site.getAddress(),
				site.getStartDate(), site.getEndDate(), site.getManager().getFullName(), isFinished(site, today)));
	}

	public SiteDetails get(Long id) {
		Site site = sites.findWithManagerById(id).orElseThrow(() -> new NotFoundException(Site.class, id));
		return new SiteDetails(site.getId(), site.getCode(), site.getName(), site.getAddress(),
				site.getDescription(), site.getStartDate(), site.getEndDate(), site.getManager().getId(),
				site.getManager().getFullName(), isFinished(site, LocalDate.now(clock)),
				dailyLogs.countBySite_Id(id),
				AWAITING_SIGN_OFF.stream().mapToLong(status -> dailyLogs.countBySite_IdAndStatus(id, status)).sum(),
				incidents.countBySite_IdAndStatusIn(id, OPEN_INCIDENT),
				correctiveActions.countByIncident_Site_IdAndStatusIn(id, OPEN_ACTION),
				dailyLogs.findLastLogDateBySiteId(id).orElse(null));
	}

	/** The site without KPIs (header of the daily log pages). */
	public SiteListItem getSummary(Long id) {
		Site site = sites.findWithManagerById(id).orElseThrow(() -> new NotFoundException(Site.class, id));
		return new SiteListItem(site.getId(), site.getCode(), site.getName(), site.getAddress(), site.getStartDate(),
				site.getEndDate(), site.getManager().getFullName(), isFinished(site, LocalDate.now(clock)));
	}

	/** Active users with the SITE_MANAGER role, offered in the site form. */
	public List<UserOption> managerOptions() {
		return users.findByRole_NameAndActiveTrueOrderByFullNameAsc(RoleName.SITE_MANAGER).stream()
			.map(user -> new UserOption(user.getId(), user.getFullName(), user.getEmail()))
			.toList();
	}

	@Transactional
	public Long create(SiteCommand command) {
		if (sites.existsByCodeIgnoreCase(command.code())) {
			throw new InvalidFieldException("code", CODE_DUPLICATE);
		}
		Site site = new Site(command.code(), command.name(), command.address(), command.startDate(),
				loadManager(command.managerId()));
		site.setDescription(command.description());
		site.setEndDate(command.endDate());
		return sites.save(site).getId();
	}

	@Transactional
	public void update(Long id, SiteCommand command) {
		Site site = sites.findById(id).orElseThrow(() -> new NotFoundException(Site.class, id));
		if (sites.existsByCodeIgnoreCaseAndIdNot(command.code(), id)) {
			throw new InvalidFieldException("code", CODE_DUPLICATE);
		}
		site.setCode(command.code());
		site.setName(command.name());
		site.setAddress(command.address());
		site.setDescription(command.description());
		site.setStartDate(command.startDate());
		site.setEndDate(command.endDate());
		site.setManager(loadManager(command.managerId()));
	}

	@Transactional
	public void delete(Long id) {
		Site site = sites.findById(id).orElseThrow(() -> new NotFoundException(Site.class, id));
		if (dailyLogs.existsBySite_Id(id) || incidents.existsBySite_Id(id) || sites.existsIncidentDraftBySiteId(id)) {
			throw new DeletionNotAllowedException(DELETE_BLOCKED, "site " + id + " has records");
		}
		sites.delete(site);
	}

	/** The manager must be an active SITE_MANAGER (a CHECK constraint cannot look at another table). */
	private AppUser loadManager(Long managerId) {
		return users.findById(managerId)
			.filter(AppUser::isActive)
			.filter(user -> user.getRole().getName() == RoleName.SITE_MANAGER)
			.orElseThrow(() -> new InvalidFieldException("managerId", MANAGER_INVALID));
	}

	/** A site is finished once its end date is reached; a future end date is only planned. */
	static boolean isFinished(Site site, LocalDate today) {
		return site.getEndDate() != null && !site.getEndDate().isAfter(today);
	}

}
