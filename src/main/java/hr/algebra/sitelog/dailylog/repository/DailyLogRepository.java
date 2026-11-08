package hr.algebra.sitelog.dailylog.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import jakarta.persistence.LockModeType;

/**
 * Queries of the DailyLog aggregate. Every association is LAZY and open-in-view is off, so each
 * use case says here what it loads.
 */
public interface DailyLogRepository extends JpaRepository<DailyLog, Long> {

	/**
	 * Paged log list of a site with the to-one data shown on each row (site, signer) fetched in
	 * the same query. Work entries are NOT loaded: touching {@code getWorkEntries()} on each row
	 * would issue one query per log (the N+1 documented in {@code docs/performance.md}); use
	 * {@link #findSummariesBySiteId} for counts and totals.
	 */
	@EntityGraph(attributePaths = { "site", "signedOffBy" })
	Page<DailyLog> findBySite_Id(Long siteId, Pageable pageable);

	/**
	 * Log list with counts per row in one statement (the N+1 fix): number of work entries,
	 * distinct crews and total crew hours per log. Sorting by {@code d.logDate} etc. comes from
	 * the {@link Pageable}.
	 */
	@Query(value = """
			select new hr.algebra.sitelog.dailylog.repository.DailyLogSummary(
			    d.id, d.logDate, d.status, d.weather, d.revision,
			    count(distinct w.id), count(distinct a.crew.id), sum(a.hoursWorked))
			from DailyLog d
			left join d.workEntries w
			left join w.assignments a
			where d.site.id = :siteId
			group by d.id, d.logDate, d.status, d.weather, d.revision""",
			countQuery = "select count(d) from DailyLog d where d.site.id = :siteId")
	Page<DailyLogSummary> findSummariesBySiteId(@Param("siteId") Long siteId, Pageable pageable);

	/**
	 * Log detail: the log, its site and signer, its work entries, their crew assignments and the
	 * crew of each assignment, all in one SQL statement. Both collections are Sets, so the
	 * repeated rows of the join (one per assignment) do not duplicate work entries.
	 */
	@Query("""
			select distinct d from DailyLog d
			join fetch d.site
			left join fetch d.signedOffBy
			left join fetch d.workEntries w
			left join fetch w.assignments a
			left join fetch a.crew
			where d.id = :id""")
	Optional<DailyLog> findDetailById(@Param("id") Long id);

	/**
	 * Loads the log for a change to one of its children (work entry, crew assignment) and forces
	 * {@code version = version + 1} at commit (ADR-0001 item 6): two concurrent edits of the same
	 * log, even on different children, then conflict on {@code daily_log.version} (409).
	 * On a SIGNED_OFF log the forced UPDATE is also rejected by the V3 lock trigger.
	 *
	 * <p>No {@code join fetch}: Hibernate applies the lock mode to every entity the query fetches,
	 * and {@code Site} has no {@code @Version}, so fetching it here fails with "has no version and
	 * may not be locked at level OPTIMISTIC_FORCE_INCREMENT". The site stays a lazy proxy.
	 */
	@Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
	@Query("select d from DailyLog d where d.id = :id")
	Optional<DailyLog> findForChildUpdateById(@Param("id") Long id);

	/** Header of the child pages (work entry / crew assignment forms): the log and its site, no children. */
	@EntityGraph(attributePaths = "site")
	Optional<DailyLog> findWithSiteById(Long id);

	/** Uniqueness check before insert/update ({@code uk_daily_log_site_date}). */
	boolean existsBySite_IdAndLogDate(Long siteId, LocalDate logDate);

	boolean existsBySite_IdAndLogDateAndIdNot(Long siteId, LocalDate logDate, Long id);

	/** Delete rule for sites: a site with daily logs cannot be deleted. */
	boolean existsBySite_Id(Long siteId);

	// ---- site KPIs ----

	long countBySite_Id(Long siteId);

	long countBySite_IdAndStatus(Long siteId, LogStatus status);

	/** Latest log date of a site (shown on the site detail), empty when the site has no logs. */
	@Query("select max(d.logDate) from DailyLog d where d.site.id = :siteId")
	Optional<LocalDate> findLastLogDateBySiteId(@Param("siteId") Long siteId);

	/**
	 * Logs not signed off whose day is before {@code before} (job "unsigned > 24h", milestone M4);
	 * the site and its manager are fetched because the notification goes to the manager.
	 */
	@EntityGraph(attributePaths = { "site", "site.manager" })
	List<DailyLog> findByStatusInAndLogDateBefore(Collection<LogStatus> statuses, LocalDate before);

}
