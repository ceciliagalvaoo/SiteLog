package hr.algebra.sitelog.dailylog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;

import hr.algebra.sitelog.TestcontainersConfiguration;
import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.exception.LogLockedException;
import hr.algebra.sitelog.crew.service.CrewCommand;
import hr.algebra.sitelog.crew.service.CrewOption;
import hr.algebra.sitelog.crew.service.CrewService;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.domain.Weather;
import hr.algebra.sitelog.site.service.SiteCommand;
import hr.algebra.sitelog.site.service.SiteService;

/**
 * End-to-end check of the M2-07 service rules against PostgreSQL. Not {@code @Transactional}: each
 * service call commits, which is what makes OPTIMISTIC_FORCE_INCREMENT and the triggers run.
 * {@code @DirtiesContext} throws the container (and the rows created here) away afterwards.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DailyLogAggregateFlowTests {

	@Autowired
	SiteService siteService;

	@Autowired
	CrewService crewService;

	@Autowired
	DailyLogService dailyLogService;

	@Autowired
	WorkEntryService workEntryService;

	@Autowired
	CrewAssignmentService crewAssignmentService;

	@Autowired
	JdbcTemplate jdbc;

	Long siteId;

	Long crewA;

	Long crewB;

	@BeforeEach
	void site() {
		Long managerId = jdbc.queryForObject("select id from app_user where email = 'manager@sitelog.test'", Long.class);
		String code = "IT-" + System.nanoTime() % 100000;
		siteId = siteService.create(new SiteCommand(code, "Integration site", "Test street 1", null,
				LocalDate.now().minusYears(1), null, managerId));
		List<CrewOption> crews = crewService.activeOptions();
		crewA = crews.get(0).id();
		crewB = crews.get(1).id();
	}

	private DailyLogCommand logWith(LocalDate date, CrewAssignmentCommand... rows) {
		return new DailyLogCommand(date, Weather.SUNNY, "notes",
				List.of(new WorkEntryCommand(null, "Formwork", "Level 1", LocalTime.of(7, 0), LocalTime.of(15, 0),
						List.of(rows))));
	}

	private static CrewAssignmentCommand row(Long id, Long crewId, String hours) {
		return new CrewAssignmentCommand(id, crewId, new BigDecimal(hours), 3, "task");
	}

	@Test
	void childChangeBumpsTheLogVersionAndAStaleVersionIsRefused() {
		Long logId = dailyLogService.create(siteId, logWith(LocalDate.now().minusDays(1), row(null, crewA, "8")));
		DailyLogDetails created = dailyLogService.getDetail(logId);
		Long entryId = created.workEntries().getFirst().id();
		Long rowId = created.workEntries().getFirst().crewAssignments().getFirst().id();

		// only crew_assignment changes, yet daily_log.version goes up (OPTIMISTIC_FORCE_INCREMENT)
		crewAssignmentService.update(logId, rowId, created.version(), row(null, crewA, "6.5"));
		DailyLogDetails after = dailyLogService.getDetail(logId);
		assertThat(after.version()).isEqualTo(created.version() + 1);
		assertThat(after.totalHours()).isEqualByComparingTo("6.5");

		// a second user still holding the old version gets the 409 exception
		assertThatThrownBy(() -> workEntryService.delete(logId, entryId, created.version()))
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

	@Test
	void fullFormUpdateSynchronisesNestedRowsAndReusesARemovedCrewRow() {
		Long logId = dailyLogService.create(siteId,
				logWith(LocalDate.now().minusDays(2), row(null, crewA, "8"), row(null, crewB, "4")));
		DailyLogDetails log = dailyLogService.getDetail(logId);
		WorkEntryView entry = log.workEntries().getFirst();
		Long rowB = entry.crewAssignments().stream().filter(r -> r.crewId().equals(crewB)).findFirst().orElseThrow().id();

		// remove crew A's row and add a new row for crew A: no unique violation, row B updated
		DailyLogCommand edit = new DailyLogCommand(log.logDate(), Weather.RAIN, null,
				List.of(new WorkEntryCommand(entry.id(), "Formwork and rebar", null, null, null,
						List.of(row(rowB, crewB, "5"), row(null, crewA, "2")))));
		dailyLogService.update(logId, log.version(), edit);

		DailyLogDetails updated = dailyLogService.getDetail(logId);
		assertThat(updated.weather()).isEqualTo(Weather.RAIN);
		assertThat(updated.workEntries()).singleElement().satisfies(e -> {
			assertThat(e.description()).isEqualTo("Formwork and rebar");
			assertThat(e.crewAssignments()).hasSize(2);
			assertThat(e.totalHours()).isEqualByComparingTo("7");
		});
		assertThat(updated.version()).isGreaterThan(log.version());
	}

	@Test
	void duplicateCrewOnOneEntryIsAFieldError() {
		assertThatThrownBy(() -> dailyLogService.create(siteId,
				logWith(LocalDate.now().minusDays(3), row(null, crewA, "8"), row(null, crewA, "1"))))
			.isInstanceOfSatisfying(InvalidFieldException.class,
					e -> assertThat(e.getField()).isEqualTo("workEntries[0].crewAssignments[1].crewId"));
	}

	@Test
	void signOffLocksTheLogAndItsChildren() {
		Long logId = dailyLogService.create(siteId, logWith(LocalDate.now().minusDays(4), row(null, crewA, "8")));
		DailyLogDetails draft = dailyLogService.getDetail(logId);
		dailyLogService.signOff(logId, draft.version());

		DailyLogDetails signed = dailyLogService.getDetail(logId);
		assertThat(signed.status()).isEqualTo(LogStatus.SIGNED_OFF);
		assertThat(signed.signedOffByName()).isNotBlank();
		assertThat(signed.editable()).isFalse();
		Long entryId = signed.workEntries().getFirst().id();

		assertThatThrownBy(() -> dailyLogService.update(logId, signed.version(), logWith(signed.logDate())))
			.isInstanceOf(LogLockedException.class);
		assertThatThrownBy(() -> workEntryService.delete(logId, entryId, signed.version()))
			.isInstanceOf(LogLockedException.class);
		assertThatThrownBy(() -> dailyLogService.delete(logId, signed.version()))
			.isInstanceOf(LogLockedException.class);
		assertThatThrownBy(() -> dailyLogService.signOff(logId, signed.version()))
			.isInstanceOf(InvalidTransitionException.class);
	}

	@Test
	void deleteRulesForSiteDraftLogAndCrew() {
		Long logId = dailyLogService.create(siteId, logWith(LocalDate.now().minusDays(5), row(null, crewA, "8")));

		assertThatThrownBy(() -> siteService.delete(siteId)).isInstanceOf(DeletionNotAllowedException.class);
		assertThatThrownBy(() -> crewService.delete(crewA)).isInstanceOf(DeletionNotAllowedException.class);

		// a DRAFT log goes with its work entries and crew rows; then the site is free to delete
		dailyLogService.delete(logId, dailyLogService.getDetail(logId).version());
		assertThat(jdbc.queryForObject("select count(*) from daily_log where id = ?", Long.class, logId)).isZero();
		siteService.delete(siteId);

		Long unused = crewService.create(new CrewCommand("Unused crew " + System.nanoTime(), "Co", "Trade", true));
		crewService.delete(unused);
		assertThat(jdbc.queryForObject("select count(*) from crew where id = ?", Long.class, unused)).isZero();
	}

}
