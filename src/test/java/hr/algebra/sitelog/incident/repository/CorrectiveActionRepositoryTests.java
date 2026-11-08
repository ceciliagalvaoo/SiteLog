package hr.algebra.sitelog.incident.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.hibernate.Hibernate;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import hr.algebra.sitelog.incident.domain.CorrectiveAction;
import hr.algebra.sitelog.incident.domain.CorrectiveActionStatus;
import hr.algebra.sitelog.incident.domain.Incident;
import hr.algebra.sitelog.incident.domain.IncidentCategory;
import hr.algebra.sitelog.incident.domain.Severity;
import hr.algebra.sitelog.support.JpaFixtures;
import hr.algebra.sitelog.support.RepositoryTest;
import jakarta.persistence.EntityManager;

/**
 * {@link CorrectiveActionRepository} on the V4 seed: OPEN actions due 15/12/2026 (ZG-02),
 * 31/01/2027 (ZG-01) and 15/02/2027 (ST-01, no assignee); one OVERDUE (ST-01); two DONE.
 */
@RepositoryTest
class CorrectiveActionRepositoryTests {

	private static final List<CorrectiveActionStatus> TO_DO = List.of(CorrectiveActionStatus.OPEN,
			CorrectiveActionStatus.OVERDUE);

	private final CorrectiveActionRepository actions;
	private final IncidentRepository incidents;
	private final JpaFixtures fixtures;

	@Autowired
	CorrectiveActionRepositoryTests(CorrectiveActionRepository actions, IncidentRepository incidents,
			EntityManager em) {
		this.actions = actions;
		this.incidents = incidents;
		this.fixtures = new JpaFixtures(em);
	}

	@Test
	void countByIncidentSiteIdAndStatusIn_countsOpenAndOverdueButNotDone() {
		assertThat(actions.countByIncident_Site_IdAndStatusIn(fixtures.site("ZG-01").getId(), TO_DO)).isEqualTo(1);
		assertThat(actions.countByIncident_Site_IdAndStatusIn(fixtures.site("ZG-02").getId(), TO_DO)).isEqualTo(1);
		assertThat(actions.countByIncident_Site_IdAndStatusIn(fixtures.site("ST-01").getId(), TO_DO)).isEqualTo(2);
		assertThat(actions.countByIncident_Site_IdAndStatusIn(fixtures.site("ST-01").getId(),
				List.of(CorrectiveActionStatus.DONE))).isEqualTo(1);
		assertThat(actions.countByIncident_Site_IdAndStatusIn(fixtures.newSite().getId(), TO_DO)).isZero();
	}

	@Test
	void findByStatusAndDueDateBefore_returnsOnlyOpenActionsStrictlyBeforeTheDay() {
		List<CorrectiveAction> due = actions.findByStatusAndDueDateBefore(CorrectiveActionStatus.OPEN,
				LocalDate.parse("2026-12-31"));

		assertThat(due).extracting(CorrectiveAction::getDescription)
			.containsExactly("Provide pallet trucks and lifting aids for block deliveries.");
		// due date equal to the parameter is not "before"
		assertThat(actions.findByStatusAndDueDateBefore(CorrectiveActionStatus.OPEN, LocalDate.parse("2026-12-15")))
			.isEmpty();
	}

	@Test
	void findByStatusAndDueDateBefore_includesActionsWithoutAssigneeAndFetchesIncidentSite() {
		fixtures.flushAndClear();
		Statistics statistics = fixtures.freshStatistics();

		List<CorrectiveAction> due = actions.findByStatusAndDueDateBefore(CorrectiveActionStatus.OPEN,
				LocalDate.parse("2027-03-01"));

		assertThat(due).hasSize(3);
		// the entity graph uses a left join on the optional assignee
		assertThat(due).filteredOn(a -> a.getAssignee() == null).hasSize(1);
		assertThat(due).extracting(a -> a.getIncident().getSite().getCode())
			.containsExactlyInAnyOrder("ZG-01", "ZG-02", "ST-01");
		assertThat(due).allSatisfy(a -> assertThat(Hibernate.isInitialized(a.getIncident())).isTrue());
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	@Test
	void save_withoutAssignee_isAllowed() {
		Incident incident = new Incident(fixtures.newSite(), Instant.parse("2026-11-03T08:00:00Z"), "Near miss",
				"Pallet tipped over.", Severity.LOW, IncidentCategory.NEAR_MISS, fixtures.user(JpaFixtures.MANAGER));
		CorrectiveAction action = new CorrectiveAction("Restack pallets", LocalDate.parse("2026-11-10"));
		incident.addCorrectiveAction(action);
		incidents.saveAndFlush(incident);
		fixtures.flushAndClear();

		CorrectiveAction loaded = actions.findById(action.getId()).orElseThrow();

		assertThat(loaded.getAssignee()).isNull();
		assertThat(loaded.getStatus()).isEqualTo(CorrectiveActionStatus.OPEN);
		assertThat(actions.countByIncident_Site_IdAndStatusIn(incident.getSite().getId(), TO_DO)).isEqualTo(1);
	}

}
