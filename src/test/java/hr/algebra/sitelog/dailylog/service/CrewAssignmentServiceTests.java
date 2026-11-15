package hr.algebra.sitelog.dailylog.service;

import static hr.algebra.sitelog.support.TestEntities.TODAY;
import static hr.algebra.sitelog.support.TestEntities.assignment;
import static hr.algebra.sitelog.support.TestEntities.crew;
import static hr.algebra.sitelog.support.TestEntities.entry;
import static hr.algebra.sitelog.support.TestEntities.log;
import static hr.algebra.sitelog.support.TestEntities.manager;
import static hr.algebra.sitelog.support.TestEntities.site;
import static hr.algebra.sitelog.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.LogLockedException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.crew.domain.Crew;
import hr.algebra.sitelog.crew.repository.CrewRepository;
import hr.algebra.sitelog.dailylog.domain.CrewAssignment;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.domain.WorkEntry;
import hr.algebra.sitelog.dailylog.repository.CrewAssignmentRepository;
import hr.algebra.sitelog.dailylog.repository.DailyLogRepository;
import hr.algebra.sitelog.dailylog.repository.WorkEntryRepository;

/** Crew rows (WorkEntry *-* Crew association entity) follow the same write rules as work entries. */
@ExtendWith(MockitoExtension.class)
class CrewAssignmentServiceTests {

	private static final Long LOG_ID = 10L;

	private static final Long ENTRY_ID = 20L;

	@Mock
	DailyLogRepository dailyLogs;

	@Mock
	WorkEntryRepository workEntries;

	@Mock
	CrewAssignmentRepository assignments;

	@Mock
	CrewRepository crews;

	CrewAssignmentService service;

	DailyLog log;

	WorkEntry entry;

	CrewAssignment row;

	Crew crewA;

	Crew crewB;

	@BeforeEach
	void setUp() {
		service = new CrewAssignmentService(dailyLogs, workEntries, assignments, new DailyLogAssembler(crews));
		crewA = crew(1L, "Alpha", true);
		crewB = crew(2L, "Bravo", true);
		log = log(LOG_ID, site(1L, "ZG-1", manager(7L)), TODAY, LogStatus.DRAFT, 2L);
		entry = entry(ENTRY_ID, "Formwork");
		row = assignment(100L, crewA, "8", 3);
		entry.addAssignment(row);
		log.addWorkEntry(entry);
	}

	private static CrewAssignmentCommand command(Long crewId, String hours) {
		return new CrewAssignmentCommand(null, crewId, new BigDecimal(hours), 4, "  Rebar tying ");
	}

	private void parentLoaded(LogStatus status) {
		log.setStatus(status);
		when(dailyLogs.findForChildUpdateById(LOG_ID)).thenReturn(Optional.of(log));
	}

	@Test
	void get_returnsRowView() {
		when(assignments.findDetailByIdAndDailyLogId(100L, LOG_ID)).thenReturn(Optional.of(row));

		CrewAssignmentView view = service.get(LOG_ID, 100L);

		assertThat(view.crewId()).isEqualTo(1L);
		assertThat(view.crewName()).isEqualTo("Alpha");
		assertThat(view.hoursWorked()).isEqualByComparingTo("8");
	}

	@Test
	void get_whenRowNotInThatLog_throwsNotFoundException() {
		when(assignments.findDetailByIdAndDailyLogId(100L, LOG_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(LOG_ID, 100L)).isInstanceOf(NotFoundException.class)
			.hasMessageContaining("CrewAssignment 100");
	}

	@Test
	void workEntryIdOf_returnsTheParentEntryId() {
		when(assignments.findDetailByIdAndDailyLogId(100L, LOG_ID)).thenReturn(Optional.of(row));

		assertThat(service.workEntryIdOf(LOG_ID, 100L)).isEqualTo(ENTRY_ID);
	}

	@Test
	void create_withActiveCrewNotYetOnEntry_addsRowAfterLoadingParentWithForceIncrement() {
		parentLoaded(LogStatus.DRAFT);
		when(workEntries.findDetailById(ENTRY_ID)).thenReturn(Optional.of(entry));
		when(assignments.existsByWorkEntry_IdAndCrew_Id(ENTRY_ID, 2L)).thenReturn(false);
		when(crews.findById(2L)).thenReturn(Optional.of(crewB));
		when(assignments.save(any(CrewAssignment.class))).thenAnswer(inv -> withId(inv.getArgument(0), 101L));

		Long id = service.create(LOG_ID, ENTRY_ID, 2L, command(2L, "6.5"));

		assertThat(id).isEqualTo(101L);
		verify(dailyLogs).findForChildUpdateById(LOG_ID);
		assertThat(entry.getAssignments()).hasSize(2).anySatisfy(a -> {
			assertThat(a.getCrew()).isSameAs(crewB);
			assertThat(a.getHoursWorked()).isEqualByComparingTo("6.5");
			assertThat(a.getWorkerCount()).isEqualTo(4);
			assertThat(a.getTask()).isEqualTo("Rebar tying");
			assertThat(a.getWorkEntry()).isSameAs(entry);
		});
	}

	@Test
	void create_whenCrewAlreadyOnEntry_throwsInvalidFieldDuplicate() {
		parentLoaded(LogStatus.DRAFT);
		when(workEntries.findDetailById(ENTRY_ID)).thenReturn(Optional.of(entry));
		when(assignments.existsByWorkEntry_IdAndCrew_Id(ENTRY_ID, 1L)).thenReturn(true);

		assertThatThrownBy(() -> service.create(LOG_ID, ENTRY_ID, 2L, command(1L, "2")))
			.isInstanceOfSatisfying(InvalidFieldException.class, e -> {
				assertThat(e.getField()).isEqualTo("crewId");
				assertThat(e.getMessageKey()).isEqualTo(DailyLogAssembler.CREW_DUPLICATE);
			});
		verify(assignments, never()).save(any());
	}

	@Test
	void create_withInactiveCrew_throwsInvalidFieldCrewInvalid() {
		parentLoaded(LogStatus.DRAFT);
		when(workEntries.findDetailById(ENTRY_ID)).thenReturn(Optional.of(entry));
		when(assignments.existsByWorkEntry_IdAndCrew_Id(ENTRY_ID, 3L)).thenReturn(false);
		when(crews.findById(3L)).thenReturn(Optional.of(crew(3L, "Retired", false)));

		assertThatThrownBy(() -> service.create(LOG_ID, ENTRY_ID, 2L, command(3L, "2")))
			.isInstanceOfSatisfying(InvalidFieldException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(DailyLogAssembler.CREW_INVALID));
		assertThat(entry.getAssignments()).containsExactly(row);
	}

	@Test
	void create_withoutCrew_throwsInvalidFieldCrewInvalid() {
		parentLoaded(LogStatus.DRAFT);
		when(workEntries.findDetailById(ENTRY_ID)).thenReturn(Optional.of(entry));

		assertThatThrownBy(() -> service.create(LOG_ID, ENTRY_ID, 2L, command(null, "2")))
			.isInstanceOfSatisfying(InvalidFieldException.class, e -> assertThat(e.getField()).isEqualTo("crewId"));
		verify(assignments, never()).existsByWorkEntry_IdAndCrew_Id(anyLong(), anyLong());
	}

	@Test
	void create_whenEntryBelongsToAnotherLog_throwsNotFoundException() {
		parentLoaded(LogStatus.DRAFT);
		WorkEntry foreign = entry(21L, "Other");
		log(99L, log.getSite(), TODAY, LogStatus.DRAFT, 0L).addWorkEntry(foreign);
		when(workEntries.findDetailById(21L)).thenReturn(Optional.of(foreign));

		assertThatThrownBy(() -> service.create(LOG_ID, 21L, 2L, command(2L, "2")))
			.isInstanceOf(NotFoundException.class);
		assertThat(foreign.getAssignments()).isEmpty();
	}

	@Test
	void create_whenLogSignedOff_throwsLogLockedExceptionBeforeTouchingTheEntry() {
		parentLoaded(LogStatus.SIGNED_OFF);

		assertThatThrownBy(() -> service.create(LOG_ID, ENTRY_ID, 2L, command(2L, "2")))
			.isInstanceOf(LogLockedException.class);
		verify(workEntries, never()).findDetailById(any());
		verify(assignments, never()).save(any());
	}

	@Test
	void create_whenParentVersionIsStale_throwsOptimisticLockingFailure() {
		parentLoaded(LogStatus.DRAFT);

		assertThatThrownBy(() -> service.create(LOG_ID, ENTRY_ID, 1L, command(2L, "2")))
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
		verify(assignments, never()).save(any());
	}

	@Test
	void create_whenLogMissing_throwsNotFoundException() {
		when(dailyLogs.findForChildUpdateById(LOG_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(LOG_ID, ENTRY_ID, 2L, command(2L, "2")))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void update_keepingADeactivatedCrew_isAllowedAndSkipsTheDuplicateCheck() {
		Crew retired = crew(3L, "Retired", false);
		row.setCrew(retired);
		parentLoaded(LogStatus.REOPENED);
		when(assignments.findDetailByIdAndDailyLogId(100L, LOG_ID)).thenReturn(Optional.of(row));

		service.update(LOG_ID, 100L, 2L, command(3L, "5"));

		assertThat(row.getCrew()).isSameAs(retired);
		assertThat(row.getHoursWorked()).isEqualByComparingTo("5");
		assertThat(row.getWorkerCount()).isEqualTo(4);
		verify(assignments, never()).existsByWorkEntry_IdAndCrew_Id(anyLong(), anyLong());
		verify(crews, never()).findById(any());
	}

	@Test
	void update_changingToAnotherActiveCrew_resolvesTheNewCrew() {
		parentLoaded(LogStatus.DRAFT);
		when(assignments.findDetailByIdAndDailyLogId(100L, LOG_ID)).thenReturn(Optional.of(row));
		when(assignments.existsByWorkEntry_IdAndCrew_Id(ENTRY_ID, 2L)).thenReturn(false);
		when(crews.findById(2L)).thenReturn(Optional.of(crewB));

		service.update(LOG_ID, 100L, 2L, command(2L, "3"));

		assertThat(row.getCrew()).isSameAs(crewB);
	}

	@Test
	void update_changingToACrewAlreadyOnTheEntry_throwsInvalidFieldDuplicateAndKeepsTheRow() {
		parentLoaded(LogStatus.DRAFT);
		when(assignments.findDetailByIdAndDailyLogId(100L, LOG_ID)).thenReturn(Optional.of(row));
		when(assignments.existsByWorkEntry_IdAndCrew_Id(ENTRY_ID, 2L)).thenReturn(true);

		assertThatThrownBy(() -> service.update(LOG_ID, 100L, 2L, command(2L, "3")))
			.isInstanceOfSatisfying(InvalidFieldException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(DailyLogAssembler.CREW_DUPLICATE));
		assertThat(row.getCrew()).isSameAs(crewA);
		assertThat(row.getHoursWorked()).isEqualByComparingTo("8");
	}

	@Test
	void update_whenLogSignedOff_throwsLogLockedExceptionAndKeepsTheRow() {
		parentLoaded(LogStatus.SIGNED_OFF);

		assertThatThrownBy(() -> service.update(LOG_ID, 100L, 2L, command(1L, "1")))
			.isInstanceOf(LogLockedException.class);
		assertThat(row.getHoursWorked()).isEqualByComparingTo("8");
		verify(assignments, never()).findDetailByIdAndDailyLogId(any(), any());
	}

	@Test
	void delete_removesTheRowFromItsEntry() {
		parentLoaded(LogStatus.DRAFT);
		when(assignments.findDetailByIdAndDailyLogId(100L, LOG_ID)).thenReturn(Optional.of(row));

		service.delete(LOG_ID, 100L, 2L);

		assertThat(entry.getAssignments()).isEmpty();
		assertThat(row.getWorkEntry()).isNull();
	}

	@Test
	void delete_whenLogSignedOff_throwsLogLockedExceptionAndKeepsTheRow() {
		parentLoaded(LogStatus.SIGNED_OFF);

		assertThatThrownBy(() -> service.delete(LOG_ID, 100L, 2L)).isInstanceOf(LogLockedException.class);
		assertThat(entry.getAssignments()).containsExactly(row);
	}

	@Test
	void delete_whenParentVersionIsStale_throwsOptimisticLockingFailure() {
		parentLoaded(LogStatus.DRAFT);

		assertThatThrownBy(() -> service.delete(LOG_ID, 100L, 7L))
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
		assertThat(entry.getAssignments()).containsExactly(row);
	}

}
