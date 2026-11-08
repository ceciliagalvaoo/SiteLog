package hr.algebra.sitelog.incident.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import hr.algebra.sitelog.crew.domain.Crew;
import hr.algebra.sitelog.incident.domain.CorrectiveAction;
import hr.algebra.sitelog.incident.domain.CorrectiveActionStatus;
import hr.algebra.sitelog.incident.domain.Incident;
import hr.algebra.sitelog.incident.domain.IncidentCategory;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.domain.Severity;
import hr.algebra.sitelog.support.JpaFixtures;
import hr.algebra.sitelog.support.RepositoryTest;
import jakarta.persistence.EntityManager;

/**
 * {@link IncidentRepository} on the V4 seed. ZG-01: "Worker slipped" (REPORTED) and "Fall from
 * slab edge" (HIGH, SIGNED_OFF, 2 crews, 2 actions); ST-01: "Electric shock" (CLOSED) and
 * "Scaffold tube dropped" (UNDER_REVIEW, no daily log, one action without assignee).
 */
@RepositoryTest
class IncidentRepositoryTests {

	private final IncidentRepository incidents;
	private final EntityManager em;
	private final JdbcTemplate jdbc;
	private final PlatformTransactionManager transactionManager;
	private final JpaFixtures fixtures;

	@Autowired
	IncidentRepositoryTests(IncidentRepository incidents, EntityManager em, JdbcTemplate jdbc,
			PlatformTransactionManager transactionManager) {
		this.incidents = incidents;
		this.em = em;
		this.jdbc = jdbc;
		this.transactionManager = transactionManager;
		this.fixtures = new JpaFixtures(em);
	}

	@Test
	void findDetailById_loadsWholeIncidentInOneStatementWithoutDuplicates() {
		long id = incidentId("Fall from slab edge");
		fixtures.flushAndClear();
		Statistics statistics = fixtures.freshStatistics();

		Incident incident = incidents.findDetailById(id).orElseThrow();

		// 2 crews x 2 actions = 4 joined rows, still 2 + 2 elements
		assertThat(incident.getAffectedCrews()).extracting(Crew::getName)
			.containsExactly("Jurić Steel Fixers", "Kovač Formwork");
		assertThat(incident.getCorrectiveActions())
			.extracting(CorrectiveAction::getStatus, a -> a.getAssignee().getEmail())
			.containsExactlyInAnyOrder(tuple(CorrectiveActionStatus.DONE, JpaFixtures.MANAGER),
					tuple(CorrectiveActionStatus.OPEN, JpaFixtures.SUPERVISOR));
		assertThat(incident.getSite().getCode()).isEqualTo("ZG-01");
		assertThat(incident.getDailyLog().getLogDate()).isEqualTo(LocalDate.parse("2026-10-27"));
		assertThat(incident.getReportedBy().getEmail()).isEqualTo(JpaFixtures.MANAGER);
		assertThat(incident.getSignedOffBy().getEmail()).isEqualTo(JpaFixtures.SUPERVISOR);
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
		assertThat(statistics.getEntityFetchCount()).isZero();
		assertThat(statistics.getCollectionFetchCount()).isZero();
	}

	@Test
	void findDetailById_whenNoDailyLogAndActionWithoutAssignee_returnsNullsNotMissingRows() {
		long id = incidentId("Scaffold tube dropped from level 4");
		fixtures.flushAndClear();

		Incident incident = incidents.findDetailById(id).orElseThrow();

		// left joins: the optional daily log, signer and assignee do not drop the incident or the action
		assertThat(incident.getDailyLog()).isNull();
		assertThat(incident.getSignedOffBy()).isNull();
		assertThat(incident.getCorrectiveActions()).hasSize(2);
		assertThat(incident.getCorrectiveActions()).filteredOn(a -> a.getAssignee() == null)
			.extracting(CorrectiveAction::getDescription)
			.containsExactly("Review the exclusion zone below the scaffold.");
	}

	@Test
	void findDetailById_whenIncidentHasNoCrewsOrActions_isStillFound() {
		Incident incident = new Incident(fixtures.newSite(), Instant.parse("2026-11-03T08:00:00Z"), "Near miss",
				"Pallet tipped over.", Severity.LOW, IncidentCategory.NEAR_MISS, fixtures.user(JpaFixtures.MANAGER));
		incidents.save(incident);
		fixtures.flushAndClear();

		Incident loaded = incidents.findDetailById(incident.getId()).orElseThrow();

		assertThat(loaded.getAffectedCrews()).isEmpty();
		assertThat(loaded.getCorrectiveActions()).isEmpty();
		assertThat(loaded.getStatus()).isEqualTo(IncidentStatus.REPORTED);
	}

	@Test
	void save_persistsAffectedCrewsInJoinTable() {
		Crew crewA = fixtures.newCrew(true);
		Crew crewB = fixtures.newCrew(true);
		Incident incident = new Incident(fixtures.newSite(), Instant.parse("2026-11-03T08:00:00Z"), "Near miss",
				"Pallet tipped over.", Severity.LOW, IncidentCategory.NEAR_MISS, fixtures.user(JpaFixtures.MANAGER));
		incident.addAffectedCrew(crewA);
		incident.addAffectedCrew(crewB);
		incident.addCorrectiveAction(new CorrectiveAction("Restack pallets", LocalDate.parse("2026-11-10")));
		incidents.saveAndFlush(incident);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM incident_affected_crew WHERE incident_id = ?",
				Integer.class, incident.getId())).isEqualTo(2);
		assertThat(incidents.existsByAffectedCrews_Id(crewA.getId())).isTrue();
	}

	@Test
	void findBySiteId_fetchesSiteAndReporterPerRow() {
		long siteId = fixtures.site("ST-01").getId();
		fixtures.flushAndClear();
		Statistics statistics = fixtures.freshStatistics();

		Page<Incident> page = incidents.findBySite_Id(siteId, PageRequest.of(0, 10, Sort.by("occurredAt")));

		assertThat(page.getContent()).extracting(Incident::getTitle)
			.containsExactly("Electric shock from distribution board", "Scaffold tube dropped from level 4");
		assertThat(page.getContent()).allSatisfy(incident -> {
			assertThat(Hibernate.isInitialized(incident.getSite())).isTrue();
			assertThat(Hibernate.isInitialized(incident.getReportedBy())).isTrue();
		});
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	@Test
	void findAllWithSite_pagesAcrossSites() {
		Page<Incident> page = incidents.findAllWithSite(PageRequest.of(0, 4, Sort.by("occurredAt")));

		assertThat(page.getTotalElements()).isEqualTo(6);
		assertThat(page.getContent()).hasSize(4);
		assertThat(page.getContent()).allSatisfy(i -> assertThat(Hibernate.isInitialized(i.getSite())).isTrue());
	}

	@Test
	void existsByAffectedCrewsId_isTrueOnlyForCrewsListedOnAnIncident() {
		assertThat(incidents.existsByAffectedCrews_Id(fixtures.crew("Elektro Novak").getId())).isTrue();
		assertThat(incidents.existsByAffectedCrews_Id(fixtures.crew("Perić Plumbing").getId())).isFalse();
	}

	@Test
	void existsBySiteId_andExistsByDailyLogId_reflectReferences() {
		assertThat(incidents.existsBySite_Id(fixtures.site("ZG-01").getId())).isTrue();
		assertThat(incidents.existsBySite_Id(fixtures.newSite().getId())).isFalse();
		assertThat(incidents.existsByDailyLog_Id(fixtures.dailyLog("ZG-01", "2026-10-27").getId())).isTrue();
		assertThat(incidents.existsByDailyLog_Id(fixtures.dailyLog("ZG-01", "2026-10-26").getId())).isFalse();
	}

	@Test
	void countBySiteIdAndStatusIn_countsOpenIncidentsPerSite() {
		List<IncidentStatus> open = List.of(IncidentStatus.REPORTED, IncidentStatus.UNDER_REVIEW);

		assertThat(incidents.countBySite_IdAndStatusIn(fixtures.site("ZG-01").getId(), open)).isEqualTo(1);
		assertThat(incidents.countBySite_IdAndStatusIn(fixtures.site("ZG-02").getId(), open)).isEqualTo(1);
		assertThat(incidents.countBySite_IdAndStatusIn(fixtures.site("ST-01").getId(), open)).isEqualTo(1);
		assertThat(incidents.countBySite_Id(fixtures.site("ST-01").getId())).isEqualTo(2);
		assertThat(incidents.countBySite_IdAndStatusIn(fixtures.site("ST-01").getId(),
				List.of(IncidentStatus.CLOSED))).isEqualTo(1);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void findForChildUpdateById_incrementsIncidentVersionAtCommit() {
		long siteId = jdbc.queryForObject("""
				INSERT INTO site (code, name, address, start_date, manager_id)
				SELECT ?, 'Incident lock site', 'Test street 1', DATE '2026-01-01', id
				FROM app_user WHERE email = ?
				RETURNING id""", Long.class, "T-" + UUID.randomUUID().toString().substring(0, 8),
				JpaFixtures.MANAGER);
		long incidentId = jdbc.queryForObject("""
				INSERT INTO incident (site_id, occurred_at, title, description, severity, category, reported_by)
				SELECT ?, now(), 'Lock test', 'Lock test', 'LOW', 'NEAR_MISS', id FROM app_user WHERE email = ?
				RETURNING id""", Long.class, siteId, JpaFixtures.MANAGER);
		try {
			long before = incidentVersion(incidentId);

			new TransactionTemplate(transactionManager)
				.executeWithoutResult(status -> incidents.findForChildUpdateById(incidentId).orElseThrow());

			assertThat(incidentVersion(incidentId)).isEqualTo(before + 1);
		}
		finally {
			jdbc.update("DELETE FROM incident WHERE id = ?", incidentId);
			jdbc.update("DELETE FROM site WHERE id = ?", siteId);
		}
	}

	private long incidentVersion(long id) {
		return jdbc.queryForObject("SELECT version FROM incident WHERE id = ?", Long.class, id);
	}

	private long incidentId(String title) {
		return em.createQuery("select i.id from Incident i where i.title = :title", Long.class)
			.setParameter("title", title)
			.getSingleResult();
	}

}
