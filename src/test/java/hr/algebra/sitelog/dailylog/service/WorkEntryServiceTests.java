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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import hr.algebra.sitelog.common.exception.LogLockedException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.crew.domain.Crew;
import hr.algebra.sitelog.crew.repository.CrewRepository;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.domain.WorkEntry;
import hr.algebra.sitelog.dailylog.repository.DailyLogRepository;
import hr.algebra.sitelog.dailylog.repository.WorkEntryRepository;
import hr.algebra.sitelog.site.domain.Site;

/**
 * A work entry is a child of the DailyLog aggregate: every write must load the parent with
 * {@code findForChildUpdateById} (OPTIMISTIC_FORCE_INCREMENT) and check version and lock first.
 */
@ExtendWith(MockitoExtension.class)
class WorkEntryServiceTests {

	private static final Long LOG_ID = 10L;

	@Mock
	DailyLogRepository dailyLogs;

	@Mock
	WorkEntryRepository workEntries;

	@Mock
	CrewRepository crews;

	WorkEntryService service;

	Site site;

	Crew crewA;

	@BeforeEach
	void setUp() {
		service = new WorkEntryService(dailyLogs, workEntries, new DailyLogAssembler(crews));
		site = site(1L, "ZG-1", manager(7L));
		crewA = crew(1L, "Alpha", true);
	}

	private DailyLog logIn(Long id, LogStatus status, long version) {
		return log(id, site, TODAY, status, version);
	}

	/** Entry 20 of the given log, with one crew row (100, crew A). */
	private WorkEntry entryOf(DailyLog log) {
		WorkEntry entry = entry(20L, "Formwork");
		entry.addAssignment(assignment(100L, crewA, "8", 3));
		log.addWorkEntry(entry);
		return entry;
	}

	private static WorkEntryCommand command(String description, Long crewRowId) {
		return new WorkEntryCommand(null, description, "Level 3", LocalTime.of(8, 0), LocalTime.of(12, 30),
				List.of(new CrewAssignmentCommand(crewRowId, 1L, new BigDecimal("4"), 2, null)));
	}

	@Test
	void get_returnsEntryWithItsCrewRows() {
		DailyLog log = logIn(LOG_ID, LogStatus.SIGNED_OFF, 1L);
		when(workEntries.findDetailById(20L)).thenReturn(Optional.of(entryOf(log)));

		WorkEntryView view = service.get(LOG_ID, 20L);

		assertThat(view.description()).isEqualTo("Formwork");
		assertThat(view.crewAssignments()).singleElement().satisfies(row -> {
			assertThat(row.crewName()).isEqualTo("Alpha");
			assertThat(row.workerCount()).isEqualTo(3);
		});
		assertThat(view.totalHours()).isEqualByComparingTo("8");
	}

	@Test
	void get_whenEntryBelongsToAnotherLog_throwsNotFoundException() {
		DailyLog other = logIn(99L, LogStatus.DRAFT, 1L);
		when(workEntries.findDetailById(20L)).thenReturn(Optional.of(entryOf(other)));

		assertThatThrownBy(() -> service.get(LOG_ID, 20L)).isInstanceOf(NotFoundException.class)
			.hasMessageContaining("WorkEntry 20");
	}

	@Test
	void create_loadsParentWithForceIncrementAndAddsEntryToTheLog() {
		DailyLog log = logIn(LOG_ID, LogStatus.DRAFT, 2L);
		when(dailyLogs.findForChildUpdateById(LOG_ID)).thenReturn(Optional.of(log));
		when(crews.findAllById(Set.of(1L))).thenReturn(List.of(crewA));
		when(workEntries.save(any(WorkEntry.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 30L));

		Long id = service.create(LOG_ID, 2L, command("Excavation", null));

		assertThat(id).isEqualTo(30L);
		verify(dailyLogs).findForChildUpdateById(LOG_ID);
		verify(dailyLogs, never()).findById(any());
		assertThat(log.getWorkEntries()).singleElement().satisfies(entry -> {
			assertThat(entry.getDescription()).isEqualTo("Excavation");
			assertThat(entry.getEndTime()).isEqualTo(LocalTime.of(12, 30));
			assertThat(entry.getDailyLog()).isSameAs(log);
			assertThat(entry.getAssignments()).singleElement()
				.satisfies(a -> assertThat(a.getCrew()).isSameAs(crewA));
		});
	}

	@Test
	void create_whenLogSignedOff_throwsLogLockedExceptionAndSavesNothing() {
		DailyLog log = logIn(LOG_ID, LogStatus.SIGNED_OFF, 2L);
		when(dailyLogs.findForChildUpdateById(LOG_ID)).thenReturn(Optional.of(log));

		assertThatThrownBy(() -> service.create(LOG_ID, 2L, command("Excavation", null)))
			.isInstanceOf(LogLockedException.class);
		assertThat(log.getWorkEntries()).isEmpty();
		verify(workEntries, never()).save(any());
	}

	@Test
	void create_whenParentVersionIsStale_throwsOptimisticLockingFailure() {
		when(dailyLogs.findForChildUpdateById(LOG_ID)).thenReturn(Optional.of(logIn(LOG_ID, LogStatus.DRAFT, 5L)));

		assertThatThrownBy(() -> service.create(LOG_ID, 4L, command("Excavation", null)))
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
		verify(workEntries, never()).save(any());
	}

	@Test
	void create_whenLogMissing_throwsNotFoundException() {
		when(dailyLogs.findForChildUpdateById(LOG_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(LOG_ID, 0L, command("x", null))).isInstanceOf(NotFoundException.class);
	}

	@Test
	void update_appliesFieldsAndCrewRowsToTheEntry() {
		DailyLog log = logIn(LOG_ID, LogStatus.REOPENED, 2L);
		WorkEntry entry = entryOf(log);
		when(dailyLogs.findForChildUpdateById(LOG_ID)).thenReturn(Optional.of(log));
		when(workEntries.findDetailById(20L)).thenReturn(Optional.of(entry));
		when(crews.findAllById(Set.of(1L))).thenReturn(List.of(crewA));

		service.update(LOG_ID, 20L, 2L, command("Formwork level 3", 100L));

		verify(dailyLogs).findForChildUpdateById(LOG_ID);
		assertThat(entry.getDescription()).isEqualTo("Formwork level 3");
		assertThat(entry.getLocation()).isEqualTo("Level 3");
		assertThat(entry.getAssignments()).singleElement().satisfies(a -> {
			assertThat(a.getId()).isEqualTo(100L);
			assertThat(a.getHoursWorked()).isEqualByComparingTo("4");
		});
	}

	@Test
	void update_whenLogSignedOff_throwsLogLockedExceptionBeforeLoadingTheEntry() {
		when(dailyLogs.findForChildUpdateById(LOG_ID)).thenReturn(Optional.of(logIn(LOG_ID, LogStatus.SIGNED_OFF, 2L)));

		assertThatThrownBy(() -> service.update(LOG_ID, 20L, 2L, command("x", 100L)))
			.isInstanceOf(LogLockedException.class);
		verify(workEntries, never()).findDetailById(any());
	}

	@Test
	void update_whenEntryBelongsToAnotherLog_throwsNotFoundException() {
		DailyLog log = logIn(LOG_ID, LogStatus.DRAFT, 2L);
		WorkEntry foreign = entryOf(logIn(99L, LogStatus.DRAFT, 0L));
		when(dailyLogs.findForChildUpdateById(LOG_ID)).thenReturn(Optional.of(log));
		when(workEntries.findDetailById(20L)).thenReturn(Optional.of(foreign));

		assertThatThrownBy(() -> service.update(LOG_ID, 20L, 2L, command("Hijack", 100L)))
			.isInstanceOf(NotFoundException.class);
		assertThat(foreign.getDescription()).isEqualTo("Formwork");
	}

	@Test
	void delete_removesEntryFromTheLog() {
		DailyLog log = logIn(LOG_ID, LogStatus.DRAFT, 2L);
		WorkEntry entry = entryOf(log);
		when(dailyLogs.findForChildUpdateById(LOG_ID)).thenReturn(Optional.of(log));
		when(workEntries.findDetailById(20L)).thenReturn(Optional.of(entry));

		service.delete(LOG_ID, 20L, 2L);

		assertThat(log.getWorkEntries()).isEmpty();
		assertThat(entry.getDailyLog()).isNull();
	}

	@Test
	void delete_whenLogSignedOff_throwsLogLockedExceptionAndKeepsEntry() {
		DailyLog log = logIn(LOG_ID, LogStatus.SIGNED_OFF, 2L);
		WorkEntry entry = entryOf(log);
		when(dailyLogs.findForChildUpdateById(LOG_ID)).thenReturn(Optional.of(log));

		assertThatThrownBy(() -> service.delete(LOG_ID, 20L, 2L)).isInstanceOf(LogLockedException.class);
		assertThat(log.getWorkEntries()).containsExactly(entry);
	}

	@Test
	void delete_whenParentVersionIsStale_throwsOptimisticLockingFailure() {
		when(dailyLogs.findForChildUpdateById(LOG_ID)).thenReturn(Optional.of(logIn(LOG_ID, LogStatus.DRAFT, 3L)));

		assertThatThrownBy(() -> service.delete(LOG_ID, 20L, 2L))
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

}
