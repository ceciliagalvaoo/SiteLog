package hr.algebra.sitelog.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.TestcontainersConfiguration;
import hr.algebra.sitelog.crew.domain.Crew;
import hr.algebra.sitelog.crew.repository.CrewRepository;
import hr.algebra.sitelog.dailylog.domain.CrewAssignment;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.WorkEntry;
import hr.algebra.sitelog.dailylog.repository.DailyLogRepository;
import hr.algebra.sitelog.dailylog.repository.DailyLogSummary;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.site.repository.SiteRepository;
import hr.algebra.sitelog.user.repository.AppUserRepository;
import jakarta.persistence.EntityManager;

/**
 * Reproduces the N+1 of the site's daily log list (docs/performance.md) by counting the JDBC
 * statements Hibernate prepares (Hibernate statistics): 30 logs, 2 work entries each, 2 crew rows
 * per entry, first page of 20.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class DailyLogListQueryCountTests {

	static final int LOGS = 30;

	static final PageRequest FIRST_PAGE = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "logDate"));

	@Autowired
	EntityManager em;

	@Autowired
	SiteRepository sites;

	@Autowired
	AppUserRepository users;

	@Autowired
	CrewRepository crews;

	@Autowired
	DailyLogRepository dailyLogs;

	@Test
	void naiveListIssuesOneQueryPerLogAndPerEntryWhileTheSummaryQueryIssuesTwo() {
		Long siteId = seed();
		Statistics stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();

		// BEFORE: page of entities, then the template walks entries -> assignments -> crew
		stats.clear();
		Page<DailyLog> page = dailyLogs.findBySite_Id(siteId, FIRST_PAGE);
		BigDecimal hours = BigDecimal.ZERO;
		for (DailyLog log : page) {
			for (WorkEntry entry : log.getWorkEntries()) {
				for (CrewAssignment assignment : entry.getAssignments()) {
					assignment.getCrew().getName();
					hours = hours.add(assignment.getHoursWorked());
				}
			}
		}
		long before = stats.getPrepareStatementCount();
		em.clear();

		// AFTER: one aggregate query (+ the count query of the page)
		stats.clear();
		Page<DailyLogSummary> summaries = dailyLogs.findSummariesBySiteId(siteId, FIRST_PAGE);
		long after = stats.getPrepareStatementCount();

		System.out.printf("N+1 daily log list (20 of %d logs): before = %d statements, after = %d statements%n",
				LOGS, before, after);
		assertThat(summaries.getContent()).hasSize(20);
		assertThat(summaries.getContent().getFirst().totalHours()).isEqualByComparingTo("16");
		assertThat(hours).isEqualByComparingTo(new BigDecimal(20 * 16));
		// 1 page + 1 count + 20 work entry collections + 40 assignment collections (+ crews)
		assertThat(before).isGreaterThanOrEqualTo(62);
		assertThat(after).isEqualTo(2);
	}

	private Long seed() {
		Site site = new Site("PERF-01", "Performance site", "Somewhere 1", LocalDate.of(2025, 1, 1),
				users.findByEmailIgnoreCase("manager@sitelog.test").orElseThrow());
		sites.save(site);
		List<Crew> active = crews.findByActiveTrueOrderByNameAsc();
		for (int day = 0; day < LOGS; day++) {
			DailyLog log = new DailyLog(site, LocalDate.of(2025, 2, 1).plusDays(day));
			for (int e = 0; e < 2; e++) {
				WorkEntry entry = new WorkEntry("Entry " + e);
				entry.setStartTime(LocalTime.of(7, 0));
				entry.setEndTime(LocalTime.of(15, 0));
				entry.addAssignment(new CrewAssignment(active.get(0), new BigDecimal("4"), 2, null));
				entry.addAssignment(new CrewAssignment(active.get(1), new BigDecimal("4"), 2, null));
				log.addWorkEntry(entry);
			}
			dailyLogs.save(log);
		}
		em.flush();
		em.clear();
		return site.getId();
	}

}
