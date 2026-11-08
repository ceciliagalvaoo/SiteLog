package hr.algebra.sitelog.dailylog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import hr.algebra.sitelog.crew.domain.Crew;
import hr.algebra.sitelog.dailylog.domain.CrewAssignment;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.domain.WorkEntry;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.support.JpaFixtures;
import hr.algebra.sitelog.support.RepositoryTest;
import jakarta.persistence.EntityManager;

/**
 * {@link DailyLogRepository} on the V4 demo data. ZG-01 has four logs: 26/10 and 27/10
 * SIGNED_OFF, 28/10 REOPENED (revision 1), 29/10 DRAFT.
 */
@RepositoryTest
class DailyLogRepositoryTests {

	private final DailyLogRepository dailyLogs;
	private final EntityManager em;
	private final JdbcTemplate jdbc;
	private final PlatformTransactionManager transactionManager;
	private final JpaFixtures fixtures;

	@Autowired
	DailyLogRepositoryTests(DailyLogRepository dailyLogs, EntityManager em, JdbcTemplate jdbc,
			PlatformTransactionManager transactionManager) {
		this.dailyLogs = dailyLogs;
		this.em = em;
		this.jdbc = jdbc;
		this.transactionManager = transactionManager;
		this.fixtures = new JpaFixtures(em);
	}

	// ---- list queries ----

	@Test
	void findBySiteId_fetchesSiteAndSignerButNotWorkEntries() {
		long siteId = fixtures.site("ZG-01").getId();
		fixtures.flushAndClear();
		Statistics statistics = fixtures.freshStatistics();

		Page<DailyLog> page = dailyLogs.findBySite_Id(siteId, PageRequest.of(0, 2, Sort.by("logDate")));

		assertThat(page.getTotalElements()).isEqualTo(4);
		assertThat(page.getContent()).extracting(DailyLog::getLogDate)
			.containsExactly(LocalDate.parse("2026-10-26"), LocalDate.parse("2026-10-27"));
		assertThat(page.getContent()).allSatisfy(log -> {
			assertThat(Hibernate.isInitialized(log.getSite())).isTrue();
			assertThat(Hibernate.isInitialized(log.getSignedOffBy())).isTrue();
			// getWorkEntries() returns an unmodifiable view, so ask the persistence unit about the attribute
			assertThat(em.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(log, "workEntries")).isFalse();
		});
		assertThat(page.getContent()).extracting(log -> log.getSignedOffBy().getEmail())
			.containsOnly(JpaFixtures.MANAGER);
		// one SELECT for the page with its to-one data, one SELECT count(*) for the total
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
	}

	@Test
	void findSummariesBySiteId_countsEntriesDistinctCrewsAndHoursPerLog() {
		Site site = fixtures.newSite();
		Crew crewA = fixtures.newCrew(true);
		Crew crewB = fixtures.newCrew(true);
		DailyLog busy = new DailyLog(site, LocalDate.parse("2026-11-03"));
		WorkEntry formwork = new WorkEntry("Formwork");
		formwork.addAssignment(fixtures.assignment(crewA, "8.00"));
		formwork.addAssignment(fixtures.assignment(crewB, "4.50"));
		WorkEntry cleanup = new WorkEntry("Cleanup");
		cleanup.addAssignment(fixtures.assignment(crewA, "1.50")); // same crew again: counted once
		busy.addWorkEntry(formwork);
		busy.addWorkEntry(cleanup);
		busy.addWorkEntry(new WorkEntry("Inspection walk")); // entry without assignments
		DailyLog empty = new DailyLog(site, LocalDate.parse("2026-11-04"));
		dailyLogs.save(busy);
		dailyLogs.save(empty);
		fixtures.flushAndClear();
		Statistics statistics = fixtures.freshStatistics();

		Page<DailyLogSummary> page = dailyLogs.findSummariesBySiteId(site.getId(),
				PageRequest.of(0, 10, Sort.by("logDate")));

		assertThat(page.getContent()).hasSize(2);
		DailyLogSummary first = page.getContent().get(0);
		assertThat(first.id()).isEqualTo(busy.getId());
		assertThat(first.workEntryCount()).isEqualTo(3);
		assertThat(first.crewCount()).isEqualTo(2);
		assertThat(first.totalHours()).isEqualByComparingTo("14.00");
		assertThat(first.status()).isEqualTo(LogStatus.DRAFT);
		DailyLogSummary second = page.getContent().get(1);
		assertThat(second.id()).isEqualTo(empty.getId());
		assertThat(second.workEntryCount()).isZero();
		assertThat(second.crewCount()).isZero();
		assertThat(second.totalHours()).isEqualByComparingTo("0");
		// the whole list in one statement: no query per row (page not full, so no count query)
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	@Test
	void findSummariesBySiteId_pagesWithCountQueryOverLogsNotJoinRows() {
		long siteId = fixtures.site("ZG-01").getId();

		Page<DailyLogSummary> page = dailyLogs.findSummariesBySiteId(siteId,
				PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "logDate")));

		// 4 logs, even though the join produces one row per crew assignment
		assertThat(page.getTotalElements()).isEqualTo(4);
		assertThat(page.getTotalPages()).isEqualTo(2);
		assertThat(page.getContent()).extracting(DailyLogSummary::logDate, DailyLogSummary::status,
				DailyLogSummary::revision)
			.containsExactly(
					org.assertj.core.groups.Tuple.tuple(LocalDate.parse("2026-10-29"), LogStatus.DRAFT, 0),
					org.assertj.core.groups.Tuple.tuple(LocalDate.parse("2026-10-28"), LogStatus.REOPENED, 1));
		assertThat(page.getContent().get(1).totalHours()).isEqualByComparingTo("14.00");
	}

	// ---- detail query ----

	@Test
	void findDetailById_loadsLogSignerEntriesAssignmentsAndCrewsInOneStatement() {
		long logId = fixtures.dailyLog("ZG-01", "2026-10-26").getId();
		fixtures.flushAndClear();
		Statistics statistics = fixtures.freshStatistics();

		DailyLog log = dailyLogs.findDetailById(logId).orElseThrow();

		assertThat(log.getSite().getCode()).isEqualTo("ZG-01");
		assertThat(log.getSignedOffBy().getEmail()).isEqualTo(JpaFixtures.MANAGER);
		assertThat(log.getWorkEntries()).hasSize(2);
		assertThat(log.getWorkEntries()).flatExtracting(WorkEntry::getAssignments)
			.extracting(a -> a.getCrew().getName(), CrewAssignment::getWorkerCount)
			.containsExactlyInAnyOrder(
					org.assertj.core.groups.Tuple.tuple("Kovač Formwork", 6),
					org.assertj.core.groups.Tuple.tuple("Jurić Steel Fixers", 2));
		// everything above was navigated without a single extra SELECT
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
		assertThat(statistics.getEntityFetchCount()).isZero();
		assertThat(statistics.getCollectionFetchCount()).isZero();
	}

	@Test
	void findDetailById_whenLogHasNoWorkEntries_returnsLogWithEmptySet() {
		DailyLog log = dailyLogs.save(new DailyLog(fixtures.newSite(), LocalDate.parse("2026-11-03")));
		fixtures.flushAndClear();

		DailyLog loaded = dailyLogs.findDetailById(log.getId()).orElseThrow();

		assertThat(loaded.getWorkEntries()).isEmpty();
		assertThat(loaded.getSignedOffBy()).isNull();
	}

	@Test
	void findDetailById_whenLogDoesNotExist_returnsEmpty() {
		assertThat(dailyLogs.findDetailById(-1L)).isEmpty();
	}

	// ---- OPTIMISTIC_FORCE_INCREMENT (ADR-0001 item 6) ----

	// The lock query must not fetch-join Site: Hibernate applies the lock mode to every fetched
	// entity, and Site has no @Version, so OPTIMISTIC_FORCE_INCREMENT would fail on it.

	// These tests commit (the forced increment is executed just before commit), so they run
	// outside the rolled-back test transaction and delete their own rows afterwards.

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void findForChildUpdateById_incrementsVersionAtCommit() {
		long siteId = insertSite();
		long logId = insertDraftLog(siteId);
		try {
			long before = version(logId);

			new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
				DailyLog log = dailyLogs.findForChildUpdateById(logId).orElseThrow();
				assertThat(log.getStatus()).isEqualTo(LogStatus.DRAFT);
				em.flush();
				// deferred: a flush alone does not touch the row yet
				assertThat(version(logId)).isEqualTo(before);
			});

			assertThat(version(logId)).isEqualTo(before + 1);
		}
		finally {
			deleteLogAndSite(logId, siteId);
		}
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void findForChildUpdateById_whenAnotherTransactionCommittedFirst_throwsOptimisticLockingFailure() {
		long siteId = insertSite();
		long logId = insertDraftLog(siteId);
		try {
			long before = version(logId);
			TransactionTemplate outer = new TransactionTemplate(transactionManager);
			TransactionTemplate inner = new TransactionTemplate(transactionManager);
			inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

			assertThatThrownBy(() -> outer.executeWithoutResult(first -> {
				dailyLogs.findForChildUpdateById(logId).orElseThrow(); // reads version N
				// a second editor of the same log (another child) commits in between: N -> N+1
				inner.executeWithoutResult(second -> dailyLogs.findForChildUpdateById(logId).orElseThrow());
			})).isInstanceOf(OptimisticLockingFailureException.class);

			// only the winner's increment is in the database
			assertThat(version(logId)).isEqualTo(before + 1);
		}
		finally {
			deleteLogAndSite(logId, siteId);
		}
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void findForChildUpdateById_whenLogIsSignedOff_commitIsRejectedByLockTrigger() {
		long logId = jdbc.queryForObject("""
				SELECT dl.id FROM daily_log dl JOIN site s ON s.id = dl.site_id
				WHERE s.code = 'ZG-01' AND dl.log_date = DATE '2026-10-26'""", Long.class);
		long before = version(logId);

		assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
			.executeWithoutResult(status -> dailyLogs.findForChildUpdateById(logId).orElseThrow()))
			.hasStackTraceContaining("daily_log_locked");

		assertThat(version(logId)).isEqualTo(before);
	}

	// ---- existence checks and KPIs ----

	@Test
	void existsBySiteId_isTrueOnlyForSitesWithLogs() {
		assertThat(dailyLogs.existsBySite_Id(fixtures.site("ZG-01").getId())).isTrue();
		assertThat(dailyLogs.existsBySite_Id(fixtures.newSite().getId())).isFalse();
	}

	@Test
	void existsBySiteIdAndLogDate_detectsTheUniqueSiteDayExceptForTheLogItself() {
		DailyLog log = fixtures.dailyLog("ZG-01", "2026-10-27");
		long siteId = log.getSite().getId();

		assertThat(dailyLogs.existsBySite_IdAndLogDate(siteId, LocalDate.parse("2026-10-27"))).isTrue();
		assertThat(dailyLogs.existsBySite_IdAndLogDate(siteId, LocalDate.parse("2026-10-30"))).isFalse();
		assertThat(dailyLogs.existsBySite_IdAndLogDateAndIdNot(siteId, LocalDate.parse("2026-10-27"), log.getId()))
			.isFalse();
		long otherLogId = fixtures.dailyLog("ZG-01", "2026-10-26").getId();
		assertThat(dailyLogs.existsBySite_IdAndLogDateAndIdNot(siteId, LocalDate.parse("2026-10-27"), otherLogId))
			.isTrue();
	}

	@Test
	void countBySiteId_andCountBySiteIdAndStatus_matchTheSeed() {
		long zg01 = fixtures.site("ZG-01").getId();
		long zg02 = fixtures.site("ZG-02").getId();

		assertThat(dailyLogs.countBySite_Id(zg01)).isEqualTo(4);
		assertThat(dailyLogs.countBySite_IdAndStatus(zg01, LogStatus.SIGNED_OFF)).isEqualTo(2);
		assertThat(dailyLogs.countBySite_IdAndStatus(zg01, LogStatus.REOPENED)).isEqualTo(1);
		assertThat(dailyLogs.countBySite_IdAndStatus(zg01, LogStatus.DRAFT)).isEqualTo(1);
		assertThat(dailyLogs.countBySite_Id(zg02)).isEqualTo(2);
		assertThat(dailyLogs.countBySite_IdAndStatus(zg02, LogStatus.REOPENED)).isZero();
	}

	@Test
	void findLastLogDateBySiteId_returnsLatestDayOrEmptyForSiteWithoutLogs() {
		assertThat(dailyLogs.findLastLogDateBySiteId(fixtures.site("ZG-01").getId()))
			.contains(LocalDate.parse("2026-10-29"));
		assertThat(dailyLogs.findLastLogDateBySiteId(fixtures.newSite().getId())).isEmpty();
	}

	@Test
	void findByStatusInAndLogDateBefore_returnsUnsignedLogsBeforeTheDayWithSiteAndManager() {
		fixtures.flushAndClear();
		Statistics statistics = fixtures.freshStatistics();

		List<DailyLog> unsigned = dailyLogs.findByStatusInAndLogDateBefore(
				List.of(LogStatus.DRAFT, LogStatus.REOPENED), LocalDate.parse("2026-10-29"));

		// ZG-01 29/10 is DRAFT but not before the day; signed-off logs are never returned
		assertThat(unsigned).extracting(log -> log.getSite().getCode() + " " + log.getLogDate())
			.containsExactlyInAnyOrder("ZG-01 2026-10-28", "ZG-02 2026-10-28", "ST-01 2026-10-28");
		assertThat(unsigned).extracting(log -> log.getSite().getManager().getEmail())
			.containsExactlyInAnyOrder(JpaFixtures.MANAGER, JpaFixtures.MANAGER, JpaFixtures.MANAGER_SPLIT);
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	// ---- committed JDBC fixtures ----

	private long insertSite() {
		return jdbc.queryForObject("""
				INSERT INTO site (code, name, address, start_date, manager_id)
				SELECT ?, 'Lock test site', 'Test street 1', DATE '2026-01-01', id
				FROM app_user WHERE email = ?
				RETURNING id""", Long.class, "T-" + UUID.randomUUID().toString().substring(0, 8),
				JpaFixtures.MANAGER);
	}

	private long insertDraftLog(long siteId) {
		return jdbc.queryForObject("""
				INSERT INTO daily_log (site_id, log_date, weather) VALUES (?, DATE '2026-11-02', 'SUNNY')
				RETURNING id""", Long.class, siteId);
	}

	private long version(long logId) {
		return jdbc.queryForObject("SELECT version FROM daily_log WHERE id = ?", Long.class, logId);
	}

	private void deleteLogAndSite(long logId, long siteId) {
		jdbc.update("DELETE FROM daily_log WHERE id = ?", logId);
		jdbc.update("DELETE FROM site WHERE id = ?", siteId);
	}

}
