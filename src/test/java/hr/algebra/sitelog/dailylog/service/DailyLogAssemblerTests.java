package hr.algebra.sitelog.dailylog.service;

import static hr.algebra.sitelog.support.TestEntities.TODAY;
import static hr.algebra.sitelog.support.TestEntities.assignment;
import static hr.algebra.sitelog.support.TestEntities.crew;
import static hr.algebra.sitelog.support.TestEntities.entry;
import static hr.algebra.sitelog.support.TestEntities.log;
import static hr.algebra.sitelog.support.TestEntities.manager;
import static hr.algebra.sitelog.support.TestEntities.site;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.crew.domain.Crew;
import hr.algebra.sitelog.crew.repository.CrewRepository;
import hr.algebra.sitelog.dailylog.domain.CrewAssignment;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.domain.WorkEntry;

@ExtendWith(MockitoExtension.class)
class DailyLogAssemblerTests {

	@Mock
	CrewRepository crews;

	DailyLogAssembler assembler;

	Crew crewA;

	Crew crewB;

	@BeforeEach
	void setUp() {
		assembler = new DailyLogAssembler(crews);
		crewA = crew(1L, "Alpha", true);
		crewB = crew(2L, "Bravo", true);
	}

	private static CrewAssignmentCommand row(Long id, Long crewId, String hours) {
		return new CrewAssignmentCommand(id, crewId, new BigDecimal(hours), 3, "Pour slab");
	}

	private static WorkEntryCommand entryCommand(Long id, String description, CrewAssignmentCommand... rows) {
		return new WorkEntryCommand(id, description, "Level 2", LocalTime.of(7, 0), LocalTime.of(15, 0), List.of(rows));
	}

	private DailyLog draftLog() {
		return log(10L, site(1L, "ZG-1", manager(7L)), TODAY, LogStatus.DRAFT, 0L);
	}

	@Test
	void apply_setsEntryFieldsAndAddsNewCrewRows() {
		when(crews.findAllById(Set.of(1L, 2L))).thenReturn(List.of(crewA, crewB));
		WorkEntry entry = new WorkEntry("old");

		assembler.apply(entry, entryCommand(null, "Concrete pour", row(null, 1L, "8"), row(null, 2L, "4.5")), "");

		assertThat(entry.getDescription()).isEqualTo("Concrete pour");
		assertThat(entry.getLocation()).isEqualTo("Level 2");
		assertThat(entry.getStartTime()).isEqualTo(LocalTime.of(7, 0));
		assertThat(entry.getEndTime()).isEqualTo(LocalTime.of(15, 0));
		assertThat(entry.getAssignments()).extracting(a -> a.getCrew().getName(), CrewAssignment::getHoursWorked)
			.containsExactlyInAnyOrder(tuple("Alpha", new BigDecimal("8")),
					tuple("Bravo", new BigDecimal("4.5")));
		assertThat(entry.getAssignments()).allSatisfy(a -> assertThat(a.getWorkEntry()).isSameAs(entry));
	}

	@Test
	void apply_loadsAllCrewsOfTheFormInOneQuery() {
		when(crews.findAllById(Set.of(1L, 2L))).thenReturn(List.of(crewA, crewB));

		assembler.apply(new WorkEntry("x"), entryCommand(null, "x", row(null, 1L, "1"), row(null, 2L, "1")), "");

		verify(crews, times(1)).findAllById(any());
		verify(crews, never()).findById(any());
	}

	@Test
	void apply_withSameCrewTwiceOnOneEntry_throwsDuplicateOnTheSecondRowPath() {
		assertThatThrownBy(() -> assembler.apply(new WorkEntry("x"),
				entryCommand(null, "x", row(null, 1L, "1"), row(null, 1L, "2")), "workEntries[2]."))
			.isInstanceOfSatisfying(InvalidFieldException.class, e -> {
				assertThat(e.getField()).isEqualTo("workEntries[2].crewAssignments[1].crewId");
				assertThat(e.getMessageKey()).isEqualTo(DailyLogAssembler.CREW_DUPLICATE);
			});
	}

	@Test
	void apply_withNewRowForInactiveCrew_throwsCrewInvalidOnThatRow() {
		Crew retired = crew(3L, "Retired", false);
		when(crews.findAllById(Set.of(1L, 3L))).thenReturn(List.of(crewA, retired));

		assertThatThrownBy(() -> assembler.apply(new WorkEntry("x"),
				entryCommand(null, "x", row(null, 1L, "1"), row(null, 3L, "2")), ""))
			.isInstanceOfSatisfying(InvalidFieldException.class, e -> {
				assertThat(e.getField()).isEqualTo("crewAssignments[1].crewId");
				assertThat(e.getMessageKey()).isEqualTo(DailyLogAssembler.CREW_INVALID);
			});
	}

	@Test
	void apply_withRowWithoutCrew_throwsCrewInvalid() {
		assertThatThrownBy(() -> assembler.apply(new WorkEntry("x"), entryCommand(null, "x", row(null, null, "1")), ""))
			.isInstanceOfSatisfying(InvalidFieldException.class,
					e -> assertThat(e.getField()).isEqualTo("crewAssignments[0].crewId"));
		verifyNoInteractions(crews);
	}

	@Test
	void apply_keepingADeactivatedCrewOnAnExistingRow_isAllowed() {
		Crew retired = crew(3L, "Retired", false);
		WorkEntry entry = entry(20L, "x");
		entry.addAssignment(assignment(100L, retired, "2", 1));
		when(crews.findAllById(Set.of(3L))).thenReturn(List.of(retired));

		assembler.apply(entry, entryCommand(20L, "x", row(100L, 3L, "6")), "");

		assertThat(entry.getAssignments()).singleElement().satisfies(a -> {
			assertThat(a.getCrew()).isSameAs(retired);
			assertThat(a.getHoursWorked()).isEqualByComparingTo("6");
		});
	}

	@Test
	void apply_removesRowsMissingFromTheForm() {
		WorkEntry entry = entry(20L, "x");
		CrewAssignment keep = assignment(100L, crewA, "2", 1);
		CrewAssignment drop = assignment(101L, crewB, "2", 1);
		entry.addAssignment(keep);
		entry.addAssignment(drop);
		when(crews.findAllById(Set.of(1L))).thenReturn(List.of(crewA));

		assembler.apply(entry, entryCommand(20L, "x", row(100L, 1L, "3")), "");

		assertThat(entry.getAssignments()).containsExactly(keep);
		assertThat(drop.getWorkEntry()).isNull();
	}

	@Test
	void apply_removingAndReAddingTheSameCrew_reusesTheExistingRowInsteadOfInsertingADuplicate() {
		WorkEntry entry = entry(20L, "x");
		CrewAssignment existing = assignment(100L, crewA, "2", 1);
		entry.addAssignment(existing);
		when(crews.findAllById(Set.of(1L))).thenReturn(List.of(crewA));

		assembler.apply(entry, entryCommand(20L, "x", row(null, 1L, "7.5")), "");

		assertThat(entry.getAssignments()).singleElement().satisfies(a -> {
			assertThat(a).isSameAs(existing);
			assertThat(a.getId()).isEqualTo(100L);
			assertThat(a.getHoursWorked()).isEqualByComparingTo("7.5");
		});
	}

	@Test
	void apply_withUnknownCrewRowId_throwsNotFound() {
		WorkEntry entry = entry(20L, "x");

		assertThatThrownBy(() -> assembler.apply(entry, entryCommand(20L, "x", row(999L, 1L, "1")), ""))
			.isInstanceOf(NotFoundException.class)
			.hasMessageContaining("CrewAssignment 999");
	}

	@Test
	void syncWorkEntries_updatesKeptEntryAddsNewOneAndRemovesMissingOne() {
		DailyLog log = draftLog();
		WorkEntry kept = entry(20L, "old");
		WorkEntry removed = entry(21L, "to remove");
		log.addWorkEntry(kept);
		log.addWorkEntry(removed);
		when(crews.findAllById(Set.of(1L, 2L))).thenReturn(List.of(crewA, crewB));

		assembler.syncWorkEntries(log, List.of(entryCommand(20L, "changed", row(null, 1L, "8")),
				entryCommand(null, "brand new", row(null, 2L, "4"))));

		assertThat(log.getWorkEntries()).extracting(WorkEntry::getDescription)
			.containsExactlyInAnyOrder("changed", "brand new");
		assertThat(log.getWorkEntries()).contains(kept).doesNotContain(removed);
		assertThat(log.getWorkEntries()).allSatisfy(e -> assertThat(e.getDailyLog()).isSameAs(log));
		assertThat(removed.getDailyLog()).isNull();
		verify(crews, times(1)).findAllById(any());
	}

	@Test
	void syncWorkEntries_withEntryIdNotInTheLog_throwsNotFound() {
		DailyLog log = draftLog();

		assertThatThrownBy(() -> assembler.syncWorkEntries(log, List.of(entryCommand(77L, "x"))))
			.isInstanceOf(NotFoundException.class)
			.hasMessageContaining("WorkEntry 77");
	}

	@Test
	void syncWorkEntries_reportsCrewErrorsWithTheEntryIndexInThePath() {
		DailyLog log = draftLog();
		when(crews.findAllById(Set.of(1L, 9L))).thenReturn(List.of(crewA));

		assertThatThrownBy(() -> assembler.syncWorkEntries(log,
				List.of(entryCommand(null, "a", row(null, 1L, "1")), entryCommand(null, "b", row(null, 9L, "1")))))
			.isInstanceOfSatisfying(InvalidFieldException.class,
					e -> assertThat(e.getField()).isEqualTo("workEntries[1].crewAssignments[0].crewId"));
	}

	@Test
	void loadCrews_withoutCrewIds_doesNotQueryTheDatabase() {
		Map<Long, Crew> loaded = assembler.loadCrews(List.of(row(null, null, "1")));

		assertThat(loaded).isEmpty();
		verifyNoInteractions(crews);
	}

	@Test
	void resolveCrew_whenCrewNotInCache_loadsItAndCachesIt() {
		when(crews.findById(2L)).thenReturn(Optional.of(crewB));
		Map<Long, Crew> cache = new HashMap<>();

		Crew resolved = assembler.resolveCrew(2L, null, "crewId", cache);

		assertThat(resolved).isSameAs(crewB);
		assertThat(cache).containsEntry(2L, crewB);
	}

	@Test
	void resolveCrew_whenCrewDoesNotExist_throwsCrewInvalid() {
		when(crews.findById(404L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> assembler.resolveCrew(404L, null, "crewId", new HashMap<>()))
			.isInstanceOfSatisfying(InvalidFieldException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(DailyLogAssembler.CREW_INVALID));
	}

}
