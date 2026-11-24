package hr.algebra.sitelog.site.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.dailylog.repository.DailyLogRepository;
import hr.algebra.sitelog.site.repository.SiteRepository;
import hr.algebra.sitelog.user.service.CurrentUserProvider;

/**
 * The "own site" rule of the SITE_MANAGER, used in {@code @PreAuthorize} expressions as
 * {@code @siteAccess}: a site manager changes daily logs (and their work entries and crew rows)
 * only on the sites whose {@code site.manager_id} is him or her. The role itself is checked in
 * the expression ({@code hasRole('SITE_MANAGER') and @siteAccess.canManageSite(#siteId)}), this
 * bean only answers "is it the user's site?". Also usable in templates:
 * {@code sec:authorize="${@siteAccess.canManageSite(site.id)}"}.
 *
 * <p>An id that does not exist answers {@code true}: the service then throws its
 * {@code NotFoundException} and the user sees the normal 404 page instead of a misleading 403.
 * Nothing is revealed by that - every signed-in user may read every site.
 */
@Component("siteAccess")
@Transactional(readOnly = true)
public class SiteAccess {

	private final SiteRepository sites;

	private final DailyLogRepository dailyLogs;

	private final CurrentUserProvider currentUser;

	public SiteAccess(SiteRepository sites, DailyLogRepository dailyLogs, CurrentUserProvider currentUser) {
		this.sites = sites;
		this.dailyLogs = dailyLogs;
		this.currentUser = currentUser;
	}

	/** True when the signed-in user is the manager of the site (or the site does not exist). */
	public boolean canManageSite(Long siteId) {
		if (siteId == null) {
			return false;
		}
		Long userId = currentUser.currentUserId();
		return sites.existsByIdAndManager_Id(siteId, userId) || !sites.existsById(siteId);
	}

	/** True when the signed-in user manages the site of the daily log (or the log does not exist). */
	public boolean canManageLog(Long dailyLogId) {
		if (dailyLogId == null) {
			return false;
		}
		Long userId = currentUser.currentUserId();
		return dailyLogs.existsByIdAndSite_Manager_Id(dailyLogId, userId) || !dailyLogs.existsById(dailyLogId);
	}

}
