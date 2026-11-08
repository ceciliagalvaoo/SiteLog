package hr.algebra.sitelog.dailylog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import hr.algebra.sitelog.crew.domain.Crew;
import hr.algebra.sitelog.dailylog.domain.CrewAssignment;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.WorkEntry;
import hr.algebra.sitelog.support.JpaFixtures;
import hr.algebra.sitelog.support.RepositoryTest;
import jakarta.persistence.EntityManager;

/** {@link WorkEntryRepository} and {@link CrewAssignmentRepository}. */
@RepositoryTest
class WorkEntryAndCrewAssignmentRepositoryTests {

	private final WorkEntryRepository workEntries;
	private final CrewAssignmentRepository crewAssignments;
	private final DailyLogRepository dailyLogs;
	private final EntityManager em;
	private final JpaFixtures fixtures;

	@Autowired
	WorkEntryAndCrewAssignmentRepositoryTests(WorkEntryRepository workEntries,
			CrewAssignmentRepository crewAssignments, DailyLogRepository dailyLogs, EntityManager em) {
		this.workEntries = workEntries;
		this.crewAssignments = crewAssignments;
		this.dailyLogs = dailyLogs;
		this.em = em;
		this.fixtures = new JpaFixtures(em);
	}

	@Test
	void findDetailById_loadsEntryLogAssignmentsAndCrewsInOneStatement() {
		WorkEntry seeded = fixtures.dailyLog("ZG-01", "2026-10-27").getWorkEntries().iterator().next();
		long entryId = seeded.getId();
		fixtures.flushAndClear();
		Statistics statistics = fixtures.freshStatistics();

		WorkEntry entry = workEntries.findDetailById(entryId).orElseThrow();

		assertThat(entry.getDescription()).isEqualTo("Rebar fixing for level 3 slab");
		assertThat(entry.getDailyLog().getLogDate()).isEqualTo(LocalDate.parse("2026-10-27"));
		// two assignments, each with its crew; no duplicates from the join
		assertThat(entry.getAssignments()).extracting(a -> a.getCrew().getName())
			.containsExactlyInAnyOrder("Jurić Steel Fixers", "Kovač Formwork");
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	@Test
	void findDetailById_whenEntryHasNoAssignments_returnsEntryWithEmptySet() {
		DailyLog log = new DailyLog(fixtures.newSite(), LocalDate.parse("2026-11-03"));
		WorkEntry entry = new WorkEntry("Site walk");
		log.addWorkEntry(entry);
		dailyLogs.save(log);
		fixtures.flushAndClear();

		assertThat(workEntries.findDetailById(entry.getId()).orElseThrow().getAssignments()).isEmpty();
	}

	@Test
	void existsByIdAndDailyLogId_isTrueOnlyForTheEntryOfThatLog() {
		DailyLog oct27 = fixtures.dailyLog("ZG-01", "2026-10-27");
		DailyLog oct26 = fixtures.dailyLog("ZG-01", "2026-10-26");
		long entryId = oct27.getWorkEntries().iterator().next().getId();

		assertThat(workEntries.existsByIdAndDailyLog_Id(entryId, oct27.getId())).isTrue();
		// nested URL with another log's id: /daily-logs/{oct26}/work-entries/{entry of oct27}
		assertThat(workEntries.existsByIdAndDailyLog_Id(entryId, oct26.getId())).isFalse();
	}

	@Test
	void save_whenSameCrewAssignedTwiceToOneEntry_isRejectedByUniqueConstraint() {
		Crew crew = fixtures.newCrew(true);
		DailyLog log = new DailyLog(fixtures.newSite(), LocalDate.parse("2026-11-03"));
		WorkEntry entry = new WorkEntry("Formwork");
		entry.addAssignment(fixtures.assignment(crew, "4.00"));
		entry.addAssignment(fixtures.assignment(crew, "3.00"));
		log.addWorkEntry(entry);

		assertThatThrownBy(() -> dailyLogs.saveAndFlush(log))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("uk_crew_assignment_entry_crew");
	}

	@Test
	void save_whenSameCrewOnTwoDifferentEntries_isAccepted() {
		Crew crew = fixtures.newCrew(true);
		DailyLog log = new DailyLog(fixtures.newSite(), LocalDate.parse("2026-11-03"));
		WorkEntry morning = new WorkEntry("Formwork");
		morning.addAssignment(fixtures.assignment(crew, "4.00"));
		WorkEntry afternoon = new WorkEntry("Cleanup");
		afternoon.addAssignment(fixtures.assignment(crew, "3.00"));
		log.addWorkEntry(morning);
		log.addWorkEntry(afternoon);

		dailyLogs.saveAndFlush(log);

		assertThat(crewAssignments.existsByWorkEntry_IdAndCrew_Id(morning.getId(), crew.getId())).isTrue();
		assertThat(crewAssignments.existsByWorkEntry_IdAndCrew_Id(afternoon.getId(), crew.getId())).isTrue();
	}

	@Test
	void existsByWorkEntryIdAndCrewId_isFalseForACrewNotOnTheEntry() {
		WorkEntry entry = fixtures.dailyLog("ZG-01", "2026-10-27").getWorkEntries().iterator().next();

		assertThat(crewAssignments.existsByWorkEntry_IdAndCrew_Id(entry.getId(),
				fixtures.crew("Kovač Formwork").getId())).isTrue();
		assertThat(crewAssignments.existsByWorkEntry_IdAndCrew_Id(entry.getId(),
				fixtures.crew("Elektro Novak").getId())).isFalse();
	}

	@Test
	void existsByCrewId_isTrueOnlyForCrewsWithAssignments() {
		assertThat(crewAssignments.existsByCrew_Id(fixtures.crew("Kovač Formwork").getId())).isTrue();
		// inactive crew of the seed, never assigned
		assertThat(crewAssignments.existsByCrew_Id(fixtures.crew("Perić Plumbing").getId())).isFalse();
	}

	@Test
	void orphanRemoval_removingAnAssignmentDeletesItsRow() {
		Crew crew = fixtures.newCrew(true);
		DailyLog log = new DailyLog(fixtures.newSite(), LocalDate.parse("2026-11-03"));
		WorkEntry entry = new WorkEntry("Formwork");
		CrewAssignment assignment = fixtures.assignment(crew, "4.00");
		entry.addAssignment(assignment);
		log.addWorkEntry(entry);
		dailyLogs.saveAndFlush(log);
		long assignmentId = assignment.getId();

		entry.removeAssignment(assignment);
		em.flush();

		assertThat(crewAssignments.existsById(assignmentId)).isFalse();
		assertThat(crewAssignments.existsByCrew_Id(crew.getId())).isFalse();
	}

}
