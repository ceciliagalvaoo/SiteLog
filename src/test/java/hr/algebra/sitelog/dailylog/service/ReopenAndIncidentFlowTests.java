package hr.algebra.sitelog.dailylog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.annotation.DirtiesContext;

import hr.algebra.sitelog.TestcontainersConfiguration;
import hr.algebra.sitelog.audit.domain.DomainActionType;
import hr.algebra.sitelog.audit.service.DomainActionView;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.exception.LogLockedException;
import hr.algebra.sitelog.crew.service.CrewService;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.domain.ReopenStatus;
import hr.algebra.sitelog.dailylog.domain.Weather;
import hr.algebra.sitelog.incident.domain.IncidentCategory;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.domain.Severity;
import hr.algebra.sitelog.incident.service.CorrectiveActionCommand;
import hr.algebra.sitelog.incident.service.CorrectiveActionService;
import hr.algebra.sitelog.incident.service.IncidentCommand;
import hr.algebra.sitelog.incident.service.IncidentDetails;
import hr.algebra.sitelog.incident.service.IncidentService;
import hr.algebra.sitelog.site.service.SiteCommand;
import hr.algebra.sitelog.site.service.SiteService;
import hr.algebra.sitelog.support.RunAs;

/**
 * AR1 and AR2 end to end against PostgreSQL with the real security, aspect, triggers and Envers.
 * Not {@code @Transactional}: Envers writes its audit rows when a transaction commits, and the
 * lock trigger / {@code @Version} only act on real commits. {@code @DirtiesContext} throws the
 * container (and the committed rows) away afterwards.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReopenAndIncidentFlowTests {

	static final String ADMIN = "admin@sitelog.test";

	static final String MANAGER = "manager@sitelog.test";

	static final String SUPERVISOR = "supervisor@sitelog.test";

	static final String INSPECTOR = "inspector@sitelog.test";

	@Autowired
	UserDetailsService users;

	@Autowired
	SiteService siteService;

	@Autowired
	CrewService crewService;

	@Autowired
	DailyLogService dailyLogService;

	@Autowired
	LogReopenService reopenService;

	@Autowired
	DailyLogHistoryService historyService;

	@Autowired
	IncidentService incidentService;

	@Autowired
	CorrectiveActionService actionService;

	@Autowired
	JdbcTemplate jdbc;

	Long siteId;

	Long crewId;

	@BeforeEach
	void site() {
		Long managerId = jdbc.queryForObject("select id from app_user where email = ?", Long.class, MANAGER);
		String code = "RF-" + System.nanoTime() % 100000;
		siteId = RunAs.user(users, ADMIN, () -> siteService.create(new SiteCommand(code, "Reopen flow site",
				"Test street 1", null, LocalDate.now().minusYears(1), null, managerId)));
		crewId = RunAs.user(users, MANAGER, () -> crewService.activeOptions().getFirst().id());
	}

	private DailyLogCommand log(Weather weather, String hours) {
		return new DailyLogCommand(LocalDate.now().minusDays(1), weather, "notes",
				List.of(new WorkEntryCommand(null, "Formwork", "Level 1", LocalTime.of(7, 0), LocalTime.of(15, 0),
						List.of(new CrewAssignmentCommand(null, crewId, new BigDecimal(hours), 3, "task")))));
	}

	private <T> T as(String email, java.util.function.Supplier<T> action) {
		return RunAs.user(users, email, action);
	}

	private void as(String email, Runnable action) {
		RunAs.user(users, email, action);
	}

	private long count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Long.class, args);
	}

	@Test
	void signedOffLog_isReopenedThroughAnApprovedRequest_correctedAndSignedOffAgain_withTheWholeTrailRecorded() {
		Long logId = as(MANAGER, () -> dailyLogService.create(siteId, log(Weather.SUNNY, "8")));
		as(MANAGER, () -> dailyLogService.signOff(logId, as(MANAGER, () -> dailyLogService.getDetail(logId)).version()));
		DailyLogDetails signed = as(MANAGER, () -> dailyLogService.getDetail(logId));

		Long requestId = as(MANAGER, () -> reopenService.request(logId, signed.version(), "Hours of the wrong crew"));

		assertThat(count("select count(*) from notification n join app_user u on u.id = n.recipient_id "
				+ "where u.email = ? and n.type = 'REOPEN_REQUESTED' and n.link = ?", SUPERVISOR, "/daily-logs/" + logId))
			.as("the supervisor is notified").isEqualTo(1);
		// still locked while the request is pending
		assertThatThrownBy(() -> as(MANAGER, () -> dailyLogService.update(logId, signed.version(), log(Weather.RAIN, "8"))))
			.isInstanceOf(LogLockedException.class);
		// a site manager cannot approve
		assertThatThrownBy(() -> as(MANAGER, () -> reopenService.approve(logId, requestId, null)))
			.isInstanceOf(AccessDeniedException.class);

		as(SUPERVISOR, () -> reopenService.approve(logId, requestId, null));

		DailyLogDetails reopened = as(MANAGER, () -> dailyLogService.getDetail(logId));
		assertThat(reopened.status()).isEqualTo(LogStatus.REOPENED);
		assertThat(reopened.revision()).isEqualTo(1);
		assertThat(reopened.editable()).isTrue();

		as(MANAGER, () -> dailyLogService.update(logId, reopened.version(),
				new DailyLogCommand(reopened.logDate(), Weather.RAIN, "corrected", List.of(new WorkEntryCommand(
						reopened.workEntries().getFirst().id(), "Formwork", "Level 1", LocalTime.of(7, 0),
						LocalTime.of(15, 0), List.of(new CrewAssignmentCommand(
								reopened.workEntries().getFirst().crewAssignments().getFirst().id(), crewId,
								new BigDecimal("6.5"), 3, "task")))))));
		as(MANAGER, () -> dailyLogService.signOff(logId, as(MANAGER, () -> dailyLogService.getDetail(logId)).version()));
		assertThat(as(MANAGER, () -> dailyLogService.getDetail(logId)).status()).isEqualTo(LogStatus.SIGNED_OFF);

		DailyLogHistory history = as(INSPECTOR, () -> historyService.history(logId));

		assertThat(history.revision()).isEqualTo(1);
		assertThat(history.reopenRequests()).singleElement().satisfies(r -> {
			assertThat(r.status()).isEqualTo(ReopenStatus.APPROVED);
			assertThat(r.reason()).isEqualTo("Hours of the wrong crew");
			assertThat(r.decidedByName()).isEqualTo("Ivana Babić");
		});
		assertThat(history.actions()).extracting(DomainActionView::action).containsExactly(
				DomainActionType.LOG_SIGNED_OFF, DomainActionType.LOG_REOPEN_REQUESTED,
				DomainActionType.LOG_REOPEN_APPROVED, DomainActionType.LOG_SIGNED_OFF);
		assertThat(history.revisions()).as("newest first").isSortedAccordingTo(
				(a, b) -> Long.compare(b.revision(), a.revision()));
		// the correction: weather and hours, before -> after, by the manager
		assertThat(history.revisions()).anySatisfy(rev -> {
			assertThat(rev.username()).isEqualTo(MANAGER);
			assertThat(rev.userName()).isEqualTo("Marko Kovačević");
			assertThat(rev.changes()).anySatisfy(change -> {
				assertThat(change.entity()).isEqualTo("DAILY_LOG");
				assertThat(change.changeType()).isEqualTo("MODIFIED");
				assertThat(change.fields()).anySatisfy(f -> {
					assertThat(f.labelKey()).isEqualTo("history.field.weather");
					assertThat(f.before()).isEqualTo("SUNNY");
					assertThat(f.after()).isEqualTo("RAIN");
					assertThat(f.valueKeyPrefix()).isEqualTo("weather.");
				});
			});
			assertThat(rev.changes()).anySatisfy(change -> {
				assertThat(change.entity()).isEqualTo("CREW_ASSIGNMENT");
				assertThat(change.fields()).anySatisfy(f -> {
					assertThat(f.labelKey()).isEqualTo("history.field.hoursWorked");
					assertThat((BigDecimal) f.before()).isEqualByComparingTo("8");
					assertThat((BigDecimal) f.after()).isEqualByComparingTo("6.5");
				});
			});
		});
		// the approval itself: SIGNED_OFF -> REOPENED, revision 0 -> 1, by the supervisor
		assertThat(history.revisions()).anySatisfy(rev -> {
			assertThat(rev.username()).isEqualTo(SUPERVISOR);
			assertThat(rev.changes()).singleElement().satisfies(change -> assertThat(change.fields())
				.extracting(FieldChange::labelKey).contains("history.field.status", "history.field.revision"));
		});
		// the first revision created the log and its children
		assertThat(history.revisions().getLast().changes()).extracting(EntityChange::changeType).containsOnly("ADDED");
	}

	@Test
	void rejectedRequest_needsAReason_andKeepsTheLogSignedOff_andOnlyOneRequestMayBePending() {
		Long logId = as(MANAGER, () -> dailyLogService.create(siteId, log(Weather.CLOUDY, "8")));
		as(MANAGER, () -> dailyLogService.signOff(logId, as(MANAGER, () -> dailyLogService.getDetail(logId)).version()));
		Long version = as(MANAGER, () -> dailyLogService.getDetail(logId)).version();
		Long requestId = as(MANAGER, () -> reopenService.request(logId, version, "Missing note"));

		assertThatThrownBy(() -> as(MANAGER, () -> reopenService.request(logId, version, "Again")))
			.isInstanceOf(InvalidTransitionException.class);
		assertThatThrownBy(() -> as(SUPERVISOR, () -> reopenService.reject(logId, requestId, " ")))
			.isInstanceOf(InvalidFieldException.class);

		as(SUPERVISOR, () -> reopenService.reject(logId, requestId, "Put it in today's log"));

		ReopenPanel panel = as(MANAGER, () -> reopenService.panel(logId));
		assertThat(panel.logStatus()).isEqualTo(LogStatus.SIGNED_OFF);
		assertThat(panel.canRequest()).isTrue();
		assertThat(panel.history()).singleElement().satisfies(r -> {
			assertThat(r.status()).isEqualTo(ReopenStatus.REJECTED);
			assertThat(r.decisionNote()).isEqualTo("Put it in today's log");
		});
		assertThat(count("select count(*) from domain_action where entity_type = 'DAILY_LOG' and entity_id = ? "
				+ "and action = 'LOG_REOPEN_REJECTED' and outcome = 'REJECTED'", logId))
			.as("the refused empty rejection is recorded too").isEqualTo(1);
	}

	@Test
	void highIncident_cannotBeSignedOffByTheSiteManager_onlyByTheSupervisor_andClosesAfterItsActionsAreDone() {
		Instant occurred = Instant.now().minus(2, ChronoUnit.HOURS);
		Long managerId = jdbc.queryForObject("select id from app_user where email = ?", Long.class, MANAGER);
		Long incidentId = as(MANAGER, () -> incidentService.report(siteId, new IncidentCommand(occurred,
				"Fall from scaffold", "Worker fell 2 m", Severity.HIGH, IncidentCategory.FALL_FROM_HEIGHT, null,
				List.of(crewId), "Missing guard rail", "Ivan", "First aid", true)));

		assertThat(count("select count(*) from notification n join app_user u on u.id = n.recipient_id "
				+ "where u.email = ? and n.type = 'HIGH_SEVERITY_INCIDENT' and n.body_key is not null", SUPERVISOR))
			.isGreaterThanOrEqualTo(1);
		Long v0 = as(MANAGER, () -> incidentService.getDetail(incidentId)).version();
		assertThatThrownBy(() -> as(MANAGER, () -> incidentService.startReview(incidentId, v0)))
			.isInstanceOf(AccessDeniedException.class);

		as(SUPERVISOR, () -> incidentService.startReview(incidentId, v0));
		Long v1 = as(SUPERVISOR, () -> incidentService.getDetail(incidentId)).version();

		// AR2: the site manager of the site signing off a HIGH incident -> 403
		assertThatThrownBy(() -> as(MANAGER, () -> incidentService.signOff(incidentId, v1)))
			.isInstanceOf(AccessDeniedException.class);
		assertThat(count("select count(*) from domain_action where entity_id = ? and action = 'INCIDENT_SIGNED_OFF' "
				+ "and outcome = 'DENIED' and actor_id = ?", incidentId, managerId)).isEqualTo(1);

		as(SUPERVISOR, () -> incidentService.signOff(incidentId, v1));
		Long v2 = as(SUPERVISOR, () -> incidentService.getDetail(incidentId)).version();
		Long actionId = as(SUPERVISOR, () -> actionService.create(incidentId, v2,
				new CorrectiveActionCommand("Install guard rails", managerId, LocalDate.now().plusDays(3))));
		Long v3 = as(SUPERVISOR, () -> incidentService.getDetail(incidentId)).version();
		assertThat(v3).as("a child change bumps the incident version").isGreaterThan(v2);
		assertThatThrownBy(() -> as(SUPERVISOR, () -> incidentService.close(incidentId, v3)))
			.isInstanceOf(InvalidTransitionException.class);

		// the assignee (a site manager) marks his action as done
		as(MANAGER, () -> actionService.markDone(incidentId, actionId, v3));
		Long v4 = as(SUPERVISOR, () -> incidentService.getDetail(incidentId)).version();
		as(SUPERVISOR, () -> incidentService.close(incidentId, v4));

		IncidentDetails closed = as(INSPECTOR, () -> incidentService.getDetail(incidentId));
		assertThat(closed.status()).isEqualTo(IncidentStatus.CLOSED);
		assertThat(closed.signedOffByName()).isEqualTo("Ivana Babić");
		assertThat(closed.timeline()).extracting(DomainActionView::action).containsExactly(
				DomainActionType.INCIDENT_REPORTED, DomainActionType.INCIDENT_REVIEW_STARTED,
				DomainActionType.INCIDENT_SIGNED_OFF, DomainActionType.CORRECTIVE_ACTION_ADDED,
				DomainActionType.CORRECTIVE_ACTION_DONE, DomainActionType.INCIDENT_CLOSED);
	}

	@Test
	void highIncidentWithoutTheExtraFields_isRefusedOnReport() {
		assertThatThrownBy(() -> as(MANAGER, () -> incidentService.report(siteId, new IncidentCommand(
				Instant.now().minusSeconds(60), "Cable cut", "Cable damaged", Severity.CRITICAL,
				IncidentCategory.ELECTRICAL, null, List.of(), null, null, null, null))))
			.isInstanceOf(hr.algebra.sitelog.common.exception.MissingSeverityFieldsException.class);
		assertThat(count("select count(*) from incident where site_id = ?", siteId)).isZero();
	}

}
