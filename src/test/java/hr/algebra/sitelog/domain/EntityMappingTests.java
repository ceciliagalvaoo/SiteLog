package hr.algebra.sitelog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import hr.algebra.sitelog.TestcontainersConfiguration;
import hr.algebra.sitelog.common.audit.AuditRevisionListener;
import hr.algebra.sitelog.crew.repository.CrewRepository;
import hr.algebra.sitelog.dailylog.domain.CrewAssignment;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.WorkEntry;
import hr.algebra.sitelog.dailylog.repository.DailyLogRepository;
import hr.algebra.sitelog.dailylog.repository.DailyLogSummary;
import hr.algebra.sitelog.incident.domain.Incident;
import hr.algebra.sitelog.incident.repository.IncidentRepository;
import hr.algebra.sitelog.notification.domain.Notification;
import hr.algebra.sitelog.notification.domain.NotificationType;
import hr.algebra.sitelog.notification.repository.NotificationRepository;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.site.repository.SiteRepository;
import hr.algebra.sitelog.user.repository.AppUserRepository;
import jakarta.persistence.EntityManager;

/**
 * Smoke test of the JPA mappings against the Flyway schema (the context only starts when
 * {@code ddl-auto=validate} accepts every entity and Envers audit table) and of the main
 * repository queries on the V4 demo data. Detailed repository tests: M2-06.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EntityMappingTests {

	private final JdbcTemplate jdbc;
	private final TransactionTemplate tx;
	private final EntityManager em;
	private final DailyLogRepository dailyLogs;
	private final SiteRepository sites;
	private final CrewRepository crews;
	private final AppUserRepository users;
	private final NotificationRepository notifications;
	private final IncidentRepository incidents;

	@Autowired
	EntityMappingTests(JdbcTemplate jdbc, TransactionTemplate tx, EntityManager em, DailyLogRepository dailyLogs,
			SiteRepository sites, CrewRepository crews, AppUserRepository users,
			NotificationRepository notifications, IncidentRepository incidents) {
		this.jdbc = jdbc;
		this.tx = tx;
		this.em = em;
		this.dailyLogs = dailyLogs;
		this.sites = sites;
		this.crews = crews;
		this.users = users;
		this.notifications = notifications;
		this.incidents = incidents;
	}

	@Test
	void detailQueryFetchesTheWholeAggregate() {
		long logId = seededLogId("ZG-01", "2026-10-27");

		DailyLog log = tx.execute(status -> dailyLogs.findDetailById(logId).orElseThrow());

		// read after the transaction ended: only works if the query fetched everything
		assertThat(Hibernate.isInitialized(log.getSite())).isTrue();
		assertThat(log.getWorkEntries()).hasSize(1);
		WorkEntry entry = log.getWorkEntries().iterator().next();
		assertThat(entry.getAssignments()).extracting(a -> a.getCrew().getName())
			.containsExactlyInAnyOrder("Jurić Steel Fixers", "Kovač Formwork");
	}

	@Test
	void detailQueryKeepsWorkEntriesInStartTimeOrder() {
		long logId = seededLogId("ZG-01", "2026-10-26");

		DailyLog log = tx.execute(status -> dailyLogs.findDetailById(logId).orElseThrow());

		assertThat(log.getWorkEntries()).extracting(WorkEntry::getDescription)
			.containsExactly("Formwork assembly for level 3 slab", "Rebar delivery and storage");
	}

	@Test
	void incidentDetailFetchesCrewsAndActionsWithoutDuplicates() {
		for (Long id : jdbc.queryForList("SELECT id FROM incident", Long.class)) {
			Incident incident = tx.execute(status -> incidents.findDetailById(id).orElseThrow());

			assertThat(incident.getAffectedCrews()).hasSize(jdbc.queryForObject(
					"SELECT count(*) FROM incident_affected_crew WHERE incident_id = ?", Integer.class, id));
			assertThat(incident.getCorrectiveActions()).hasSize(jdbc.queryForObject(
					"SELECT count(*) FROM corrective_action WHERE incident_id = ?", Integer.class, id));
		}
	}

	@Test
	void summaryQueryComputesCountsPerLog() {
		long siteId = jdbc.queryForObject("SELECT id FROM site WHERE code = 'ZG-01'", Long.class);

		var page = tx.execute(status -> dailyLogs.findSummariesBySiteId(siteId,
				PageRequest.of(0, 20, Sort.by("logDate"))));

		DailyLogSummary oct27 = page.getContent().stream()
			.filter(s -> s.logDate().equals(LocalDate.parse("2026-10-27")))
			.findFirst().orElseThrow();
		assertThat(oct27.workEntryCount()).isEqualTo(1);
		assertThat(oct27.crewCount()).isEqualTo(2);
		assertThat(oct27.totalHours()).isEqualByComparingTo("13.00");
		assertThat(page.getTotalElements()).isEqualTo(dailyLogs.countBySite_Id(siteId));
	}

	@Test
	void notificationsAreCountedAndMarkedReadPerRecipient() {
		tx.executeWithoutResult(status -> {
			var supervisor = users.findByEmailIgnoreCase("supervisor@sitelog.test").orElseThrow();
			long before = notifications.countByRecipient_IdAndReadAtIsNull(supervisor.getId());
			notifications.save(new Notification(supervisor, NotificationType.REOPEN_REQUESTED,
					"Reopen requested", null, "/daily-logs/1", Instant.now()));

			assertThat(notifications.countByRecipient_IdAndReadAtIsNull(supervisor.getId())).isEqualTo(before + 1);
			notifications.markAllRead(supervisor.getId(), Instant.now());
			assertThat(notifications.countByRecipient_IdAndReadAtIsNull(supervisor.getId())).isZero();
			status.setRollbackOnly();
		});
	}

	@Test
	void enversWritesRevisionWithSystemUserOnCommit() {
		String code = "T-" + UUID.randomUUID().toString().substring(0, 8);
		Long logId = tx.execute(status -> {
			var manager = users.findByEmailIgnoreCase("manager@sitelog.test").orElseThrow();
			Site site = sites.save(new Site(code, "Envers test site", "Test street 1", LocalDate.parse("2026-01-01"),
					manager));
			DailyLog log = new DailyLog(site, LocalDate.parse("2026-11-02"));
			WorkEntry entry = new WorkEntry("Formwork");
			entry.addAssignment(new CrewAssignment(crews.findByActiveTrueOrderByNameAsc().getFirst(),
					new BigDecimal("8.00"), 3, "Slab"));
			log.addWorkEntry(entry);
			return dailyLogs.save(log).getId();
		});

		try {
			String username = jdbc.queryForObject("""
					SELECT r.username FROM daily_log_aud a JOIN revinfo r ON r.rev = a.rev
					WHERE a.id = ? AND a.revtype = 0""", String.class, logId);
			assertThat(username).isEqualTo(AuditRevisionListener.SYSTEM_USER);
			assertThat(jdbc.queryForObject("""
					SELECT count(*) FROM crew_assignment_aud ca JOIN work_entry_aud we ON we.id = ca.work_entry_id
					WHERE we.daily_log_id = ?""", Integer.class, logId)).isEqualTo(1);
		}
		finally {
			// remove the committed rows so other tests see only the demo data (audit rows stay: append-only)
			tx.executeWithoutResult(status -> {
				DailyLog log = em.find(DailyLog.class, logId);
				Site site = log.getSite();
				dailyLogs.delete(log);
				dailyLogs.flush();
				sites.delete(site);
			});
		}
	}

	private long seededLogId(String siteCode, String date) {
		return jdbc.queryForObject("""
				SELECT dl.id FROM daily_log dl JOIN site s ON s.id = dl.site_id
				WHERE s.code = ? AND dl.log_date = ?::date""", Long.class, siteCode, date);
	}

}
