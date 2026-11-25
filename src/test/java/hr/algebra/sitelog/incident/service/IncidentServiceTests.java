package hr.algebra.sitelog.incident.service;

import static hr.algebra.sitelog.incident.service.IncidentFixtures.action;
import static hr.algebra.sitelog.incident.service.IncidentFixtures.complete;
import static hr.algebra.sitelog.incident.service.IncidentFixtures.incident;
import static hr.algebra.sitelog.support.TestEntities.NOW;
import static hr.algebra.sitelog.support.TestEntities.TODAY;
import static hr.algebra.sitelog.support.TestEntities.crew;
import static hr.algebra.sitelog.support.TestEntities.log;
import static hr.algebra.sitelog.support.TestEntities.manager;
import static hr.algebra.sitelog.support.TestEntities.site;
import static hr.algebra.sitelog.support.TestEntities.user;
import static hr.algebra.sitelog.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import hr.algebra.sitelog.audit.domain.AuditedEntityType;
import hr.algebra.sitelog.audit.domain.DomainActionType;
import hr.algebra.sitelog.audit.service.DomainActionService;
import hr.algebra.sitelog.audit.service.DomainActionView;
import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.exception.MissingSeverityFieldsException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.crew.domain.Crew;
import hr.algebra.sitelog.crew.repository.CrewRepository;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.repository.DailyLogRepository;
import hr.algebra.sitelog.incident.domain.CorrectiveActionStatus;
import hr.algebra.sitelog.incident.domain.Incident;
import hr.algebra.sitelog.incident.domain.IncidentCategory;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.domain.Severity;
import hr.algebra.sitelog.incident.repository.IncidentRepository;
import hr.algebra.sitelog.notification.domain.NotificationType;
import hr.algebra.sitelog.notification.service.NotificationPublisher;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.site.repository.SiteRepository;
import hr.algebra.sitelog.support.TestEntities;
import hr.algebra.sitelog.user.domain.AppUser;
import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.repository.AppUserRepository;
import hr.algebra.sitelog.user.service.CurrentUser;
import hr.algebra.sitelog.user.service.CurrentUserProvider;

/** AR2: report, required fields by severity, workflow transitions and who may perform them. */
@ExtendWith(MockitoExtension.class)
class IncidentServiceTests {

	private static final Long ID = 30L;

	private static final Long SITE_ID = 1L;

	@Mock
	IncidentRepository incidents;

	@Mock
	SiteRepository sites;

	@Mock
	DailyLogRepository dailyLogs;

	@Mock
	CrewRepository crews;

	@Mock
	AppUserRepository users;

	@Mock
	CurrentUserProvider currentUser;

	@Mock
	NotificationPublisher notifications;

	@Mock
	DomainActionService domainActions;

	private IncidentService service;

	private final AppUser manager = manager(7L);

	private final AppUser supervisor = user(9L, "Ivana Babic", RoleName.SAFETY_SUPERVISOR, true);

	private final Site site = site(SITE_ID, "ZG-01", manager);

	@BeforeEach
	void setUp() {
		service = new IncidentService(incidents, sites, dailyLogs, crews, users, currentUser, notifications,
				domainActions, TestEntities.fixedClock());
	}

	private void signedInAs(AppUser user) {
		lenient().when(currentUser.currentUser()).thenReturn(CurrentUser.of(user));
		lenient().when(currentUser.currentUserId()).thenReturn(user.getId());
		lenient().when(users.getReferenceById(user.getId())).thenReturn(user);
	}

	private static IncidentCommand command(Severity severity, boolean extraFields, Long dailyLogId, List<Long> crews) {
		return new IncidentCommand(NOW.minusSeconds(600), "Scaffold tube dropped", "Tube fell from level 4", severity,
				IncidentCategory.STRUCK_BY_OBJECT, dailyLogId, crews, extraFields ? "Toe board missing" : null,
				extraFields ? "Luka Matic" : " ", extraFields ? "None" : null, extraFields ? Boolean.FALSE : null);
	}

	private Incident stored(Severity severity, IncidentStatus status) {
		Incident incident = incident(ID, site, severity, status, manager, 4);
		lenient().when(incidents.findDetailById(ID)).thenReturn(Optional.of(incident));
		return incident;
	}

	// ------------------------------------------------------------------ report

	@Test
	void report_aLowIncident_savesItWithoutTheExtraFieldsAndSendsNoNotification() {
		signedInAs(manager);
		when(sites.findById(SITE_ID)).thenReturn(Optional.of(site));
		when(incidents.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), ID));

		Long id = service.report(SITE_ID, command(Severity.LOW, false, null, List.of()));

		assertThat(id).isEqualTo(ID);
		ArgumentCaptor<Incident> saved = ArgumentCaptor.forClass(Incident.class);
		verify(incidents).save(saved.capture());
		assertThat(saved.getValue().getStatus()).isEqualTo(IncidentStatus.REPORTED);
		assertThat(saved.getValue().getReportedBy()).isSameAs(manager);
		assertThat(saved.getValue().getWitnesses()).as("blank text is stored as null").isNull();
		verifyNoInteractions(notifications);
	}

	@Test
	void report_aHighIncidentWithoutTheExtraFields_listsEveryMissingFieldAndSavesNothing() {
		signedInAs(manager);
		when(sites.findById(SITE_ID)).thenReturn(Optional.of(site));

		assertThatThrownBy(() -> service.report(SITE_ID, command(Severity.HIGH, false, null, List.of())))
			.isInstanceOfSatisfying(MissingSeverityFieldsException.class, e -> assertThat(e.getFields())
				.containsExactly("rootCause", "witnesses", "medicalTreatment", "authorityNotified"));
		verify(incidents, never()).save(any());
	}

	@Test
	void report_aCompleteCriticalIncident_notifiesTheSafetySupervisors() {
		signedInAs(manager);
		Crew crew = crew(3L, "Dalmacija Scaffolding", true);
		DailyLog log = log(12L, site, TODAY, LogStatus.DRAFT, 0);
		when(sites.findById(SITE_ID)).thenReturn(Optional.of(site));
		when(dailyLogs.existsByIdAndSite_Id(12L, SITE_ID)).thenReturn(true);
		when(dailyLogs.getReferenceById(12L)).thenReturn(log);
		when(crews.findAllById(List.of(3L))).thenReturn(List.of(crew));
		when(incidents.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), ID));

		service.report(SITE_ID, command(Severity.CRITICAL, true, 12L, List.of(3L, 3L)));

		ArgumentCaptor<Incident> saved = ArgumentCaptor.forClass(Incident.class);
		verify(incidents).save(saved.capture());
		assertThat(saved.getValue().getDailyLog()).isSameAs(log);
		assertThat(saved.getValue().getAffectedCrews()).containsExactly(crew);
		assertThat(saved.getValue().getAuthorityNotified()).isFalse();
		verify(notifications).notifyRole(RoleName.SAFETY_SUPERVISOR, NotificationType.HIGH_SEVERITY_INCIDENT,
				"/incidents/30", "ZG-01", "CRITICAL", "Scaffold tube dropped");
	}

	@Test
	void report_inTheFuture_isAFieldError() {
		signedInAs(manager);
		when(sites.findById(SITE_ID)).thenReturn(Optional.of(site));
		IncidentCommand future = new IncidentCommand(NOW.plusSeconds(3600), "t", "d", Severity.LOW,
				IncidentCategory.OTHER, null, List.of(), null, null, null, null);

		assertThatThrownBy(() -> service.report(SITE_ID, future))
			.isInstanceOfSatisfying(InvalidFieldException.class,
					e -> assertThat(e.getField()).isEqualTo("occurredAt"));
	}

	@Test
	void report_linkedToADailyLogOfAnotherSite_isAFieldError() {
		signedInAs(manager);
		when(sites.findById(SITE_ID)).thenReturn(Optional.of(site));
		when(dailyLogs.existsByIdAndSite_Id(99L, SITE_ID)).thenReturn(false);

		assertThatThrownBy(() -> service.report(SITE_ID, command(Severity.LOW, false, 99L, List.of())))
			.isInstanceOfSatisfying(InvalidFieldException.class, e -> {
				assertThat(e.getField()).isEqualTo("dailyLogId");
				assertThat(e.getMessageKey()).isEqualTo(IncidentService.DAILY_LOG_INVALID);
			});
	}

	@Test
	void report_withAnInactiveCrew_isAFieldError() {
		signedInAs(manager);
		when(sites.findById(SITE_ID)).thenReturn(Optional.of(site));
		when(crews.findAllById(List.of(3L))).thenReturn(List.of(crew(3L, "Old crew", false)));

		assertThatThrownBy(() -> service.report(SITE_ID, command(Severity.LOW, false, null, List.of(3L))))
			.isInstanceOfSatisfying(InvalidFieldException.class,
					e -> assertThat(e.getField()).isEqualTo("affectedCrewIds"));
	}

	@Test
	void report_onAnUnknownSite_isNotFound() {
		when(sites.findById(SITE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.report(SITE_ID, command(Severity.LOW, false, null, List.of())))
			.isInstanceOf(NotFoundException.class);
	}

	// ------------------------------------------------------------------ update

	@Test
	void update_raisingTheSeverityToHigh_requiresTheFieldsAndNotifiesTheSupervisors() {
		signedInAs(manager);
		Incident incident = stored(Severity.LOW, IncidentStatus.REPORTED);
		Crew keptInactive = crew(4L, "Deactivated crew", false);
		incident.addAffectedCrew(keptInactive);

		service.update(ID, 4L, command(Severity.HIGH, true, null, List.of(4L)));

		assertThat(incident.getSeverity()).isEqualTo(Severity.HIGH);
		assertThat(incident.getTitle()).isEqualTo("Scaffold tube dropped");
		assertThat(incident.getAffectedCrews()).as("an already listed crew may stay").containsExactly(keptInactive);
		verify(notifications).notifyRole(RoleName.SAFETY_SUPERVISOR, NotificationType.HIGH_SEVERITY_INCIDENT,
				"/incidents/30", "ZG-01", "HIGH", "Scaffold tube dropped");
	}

	@Test
	void update_removesCrewsThatAreNoLongerSelected() {
		signedInAs(manager);
		Incident incident = stored(Severity.LOW, IncidentStatus.REPORTED);
		incident.addAffectedCrew(crew(4L, "Crew A", true));

		service.update(ID, 4L, command(Severity.LOW, false, null, List.of()));

		assertThat(incident.getAffectedCrews()).isEmpty();
		verifyNoInteractions(notifications);
	}

	@Test
	void update_bySiteManagerLoweringAHighSeverity_isRefused() {
		signedInAs(manager);
		complete(stored(Severity.HIGH, IncidentStatus.REPORTED));

		assertThatThrownBy(() -> service.update(ID, 4L, command(Severity.LOW, false, null, List.of())))
			.isInstanceOfSatisfying(InvalidFieldException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(IncidentService.SEVERITY_DOWNGRADE));
	}

	@Test
	void update_bySupervisorLoweringAHighSeverity_isAllowed() {
		signedInAs(supervisor);
		Incident incident = complete(stored(Severity.HIGH, IncidentStatus.UNDER_REVIEW));

		service.update(ID, 4L, command(Severity.MEDIUM, false, null, List.of()));

		assertThat(incident.getSeverity()).isEqualTo(Severity.MEDIUM);
	}

	@Test
	void update_bySiteManagerWhileUnderReview_isRefused() {
		signedInAs(manager);
		stored(Severity.LOW, IncidentStatus.UNDER_REVIEW);

		assertThatThrownBy(() -> service.update(ID, 4L, command(Severity.LOW, false, null, List.of())))
			.isInstanceOfSatisfying(InvalidTransitionException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(IncidentService.UNDER_REVIEW_BY_SUPERVISOR));
	}

	@Test
	void update_ofASignedOffIncident_isRefused() {
		signedInAs(supervisor);
		stored(Severity.LOW, IncidentStatus.SIGNED_OFF);

		assertThatThrownBy(() -> service.update(ID, 4L, command(Severity.LOW, false, null, List.of())))
			.isInstanceOfSatisfying(InvalidTransitionException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(IncidentService.NOT_EDITABLE));
	}

	@Test
	void update_withAStaleVersion_isAConflict() {
		stored(Severity.LOW, IncidentStatus.REPORTED);

		assertThatThrownBy(() -> service.update(ID, 3L, command(Severity.LOW, false, null, List.of())))
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

	// ------------------------------------------------------------------ workflow

	@Test
	void startReview_movesAReportedIncidentUnderReview() {
		signedInAs(manager);
		Incident incident = stored(Severity.MEDIUM, IncidentStatus.REPORTED);

		service.startReview(ID, 4L);

		assertThat(incident.getStatus()).isEqualTo(IncidentStatus.UNDER_REVIEW);
	}

	@Test
	void startReview_ofAnIncidentAlreadyUnderReview_isRefused() {
		signedInAs(supervisor);
		stored(Severity.LOW, IncidentStatus.UNDER_REVIEW);

		assertThatThrownBy(() -> service.startReview(ID, 4L)).isInstanceOf(InvalidTransitionException.class);
	}

	@Test
	void signOff_ofAHighIncidentBySiteManager_isAccessDeniedEvenIfTheExpressionLetItThrough() {
		signedInAs(manager);
		Incident incident = complete(stored(Severity.HIGH, IncidentStatus.UNDER_REVIEW));

		assertThatThrownBy(() -> service.signOff(ID, 4L)).isInstanceOf(AccessDeniedException.class);
		assertThat(incident.getStatus()).isEqualTo(IncidentStatus.UNDER_REVIEW);
		assertThat(incident.getSignedOffBy()).isNull();
	}

	@Test
	void signOff_ofACompleteHighIncidentBySupervisor_recordsWhoAndWhen() {
		signedInAs(supervisor);
		Incident incident = complete(stored(Severity.HIGH, IncidentStatus.UNDER_REVIEW));

		service.signOff(ID, 4L);

		assertThat(incident.getStatus()).isEqualTo(IncidentStatus.SIGNED_OFF);
		assertThat(incident.getSignedOffBy()).isSameAs(supervisor);
		assertThat(incident.getSignedOffAt()).isEqualTo(NOW);
	}

	@Test
	void signOff_ofAHighIncidentWithMissingFields_isRefusedWithTheFields() {
		signedInAs(supervisor);
		Incident incident = stored(Severity.CRITICAL, IncidentStatus.UNDER_REVIEW);
		incident.setWitnesses("Luka Matic");

		assertThatThrownBy(() -> service.signOff(ID, 4L))
			.isInstanceOfSatisfying(MissingSeverityFieldsException.class,
					e -> assertThat(e.getFields()).containsExactly("rootCause", "medicalTreatment", "authorityNotified"));
		assertThat(incident.getStatus()).isEqualTo(IncidentStatus.UNDER_REVIEW);
	}

	@Test
	void signOff_ofALowIncidentBySiteManager_isAllowed() {
		signedInAs(manager);
		Incident incident = stored(Severity.LOW, IncidentStatus.UNDER_REVIEW);

		service.signOff(ID, 4L);

		assertThat(incident.getStatus()).isEqualTo(IncidentStatus.SIGNED_OFF);
		assertThat(incident.getSignedOffBy()).isSameAs(manager);
	}

	@Test
	void signOff_ofAReportedIncident_isRefused() {
		signedInAs(supervisor);
		stored(Severity.LOW, IncidentStatus.REPORTED);

		assertThatThrownBy(() -> service.signOff(ID, 4L))
			.isInstanceOfSatisfying(InvalidTransitionException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(IncidentService.SIGN_OFF_NOT_UNDER_REVIEW));
	}

	@Test
	void close_withAnOpenCorrectiveAction_isRefused() {
		signedInAs(supervisor);
		Incident incident = complete(stored(Severity.HIGH, IncidentStatus.SIGNED_OFF));
		action(1L, incident, CorrectiveActionStatus.DONE, TODAY, null);
		action(2L, incident, CorrectiveActionStatus.OVERDUE, TODAY.minusDays(2), null);

		assertThatThrownBy(() -> service.close(ID, 4L))
			.isInstanceOfSatisfying(InvalidTransitionException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(IncidentService.CLOSE_OPEN_ACTIONS));
	}

	@Test
	void close_whenEveryActionIsDone_closesTheIncident() {
		signedInAs(supervisor);
		Incident incident = complete(stored(Severity.HIGH, IncidentStatus.SIGNED_OFF));
		action(1L, incident, CorrectiveActionStatus.DONE, TODAY, null);

		service.close(ID, 4L);

		assertThat(incident.getStatus()).isEqualTo(IncidentStatus.CLOSED);
	}

	@Test
	void close_ofAnIncidentNotSignedOff_isRefused() {
		signedInAs(supervisor);
		stored(Severity.LOW, IncidentStatus.UNDER_REVIEW);

		assertThatThrownBy(() -> service.close(ID, 4L))
			.isInstanceOfSatisfying(InvalidTransitionException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(IncidentService.CLOSE_NOT_SIGNED_OFF));
	}

	@Test
	void delete_ofAReportedIncidentWithoutActions_returnsTheSite() {
		Incident incident = stored(Severity.LOW, IncidentStatus.REPORTED);

		assertThat(service.delete(ID, 4L)).isEqualTo(SITE_ID);
		verify(incidents).delete(incident);
	}

	@Test
	void delete_ofAnIncidentWithCorrectiveActions_isRefused() {
		Incident incident = stored(Severity.LOW, IncidentStatus.REPORTED);
		action(1L, incident, CorrectiveActionStatus.OPEN, TODAY, null);

		assertThatThrownBy(() -> service.delete(ID, 4L)).isInstanceOf(DeletionNotAllowedException.class);
		verify(incidents, never()).delete(any(Incident.class));
	}

	@Test
	void unknownIncident_isNotFound() {
		when(incidents.findDetailById(ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getDetail(ID)).isInstanceOf(NotFoundException.class);
	}

	// ------------------------------------------------------------------ reads

	@Test
	@SuppressWarnings("unchecked")
	void list_mapsTheRowsWithSiteAndReporter() {
		Incident incident = stored(Severity.HIGH, IncidentStatus.REPORTED);
		PageRequest pageable = PageRequest.of(0, 20);
		when(incidents.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of(incident)));

		assertThat(service.list(new IncidentFilter(SITE_ID, IncidentStatus.REPORTED, Severity.HIGH), pageable))
			.singleElement().satisfies(row -> {
				assertThat(row.siteCode()).isEqualTo("ZG-01");
				assertThat(row.reportedByName()).isEqualTo("Marko Kovacevic");
				assertThat(row.occurredAt().getZone()).isEqualTo(TestEntities.ZONE);
			});
		assertThat(service.list(null, pageable)).hasSize(1);
	}

	@Test
	void getDetail_ofSeedDataWithoutRecordedActions_buildsTheTimelineFromTheIncident() {
		Incident incident = complete(stored(Severity.HIGH, IncidentStatus.SIGNED_OFF));
		incident.setSignedOffBy(supervisor);
		incident.setSignedOffAt(NOW.minusSeconds(60));
		when(domainActions.timeline(AuditedEntityType.INCIDENT, ID)).thenReturn(List.of());

		IncidentDetails details = service.getDetail(ID);

		assertThat(details.timeline()).extracting(DomainActionView::action)
			.containsExactly(DomainActionType.INCIDENT_REPORTED, DomainActionType.INCIDENT_SIGNED_OFF);
		assertThat(details.signedOffByName()).isEqualTo("Ivana Babic");
		assertThat(details.missingSeverityFields()).isEmpty();
		assertThat(details.requiresSupervisorSignOff()).isTrue();
		assertThat(details.canClose()).isTrue();
		assertThat(details.editable()).isFalse();
	}

	@Test
	void getDetail_usesTheRecordedActionsWhenThereAreSome() {
		Incident incident = stored(Severity.HIGH, IncidentStatus.UNDER_REVIEW);
		action(1L, incident, CorrectiveActionStatus.OPEN, TODAY.minusDays(1), manager);
		DomainActionView reported = DomainActionView.of(DomainActionType.INCIDENT_REPORTED, "Marko",
				NOW.atZone(TestEntities.ZONE));
		when(domainActions.timeline(AuditedEntityType.INCIDENT, ID)).thenReturn(List.of(reported));

		IncidentDetails details = service.getDetail(ID);

		assertThat(details.timeline()).containsExactly(reported);
		assertThat(details.missingSeverityFields()).hasSize(4);
		assertThat(details.canSignOff()).isFalse();
		assertThat(details.correctiveActions()).singleElement().satisfies(view -> {
			assertThat(view.overdue()).as("OPEN after its due date shows as overdue").isTrue();
			assertThat(view.assigneeName()).isEqualTo("Marko Kovacevic");
		});
		assertThat(details.openActionCount()).isEqualTo(1);
	}

	@Test
	void getForEdit_ofAClosedIncident_isRefused() {
		signedInAs(supervisor);
		stored(Severity.LOW, IncidentStatus.CLOSED);

		assertThatThrownBy(() -> service.getForEdit(ID)).isInstanceOf(InvalidTransitionException.class);
	}

	@Test
	void getForEdit_ofAReportedIncident_returnsTheDetails() {
		signedInAs(manager);
		stored(Severity.LOW, IncidentStatus.REPORTED);

		assertThat(service.getForEdit(ID).id()).isEqualTo(ID);
	}

	@Test
	void dailyLogOptionsForReport_listsTheSitesLogs() {
		when(sites.existsById(SITE_ID)).thenReturn(true);
		when(dailyLogs.findTop60BySite_IdOrderByLogDateDesc(SITE_ID))
			.thenReturn(List.of(log(12L, site, TODAY, LogStatus.SIGNED_OFF, 1)));

		assertThat(service.dailyLogOptionsForReport(SITE_ID))
			.containsExactly(new DailyLogOption(12L, TODAY, LogStatus.SIGNED_OFF));
	}

	@Test
	void dailyLogOptionsForReport_ofAnUnknownSite_isNotFound() {
		when(sites.existsById(SITE_ID)).thenReturn(false);

		assertThatThrownBy(() -> service.dailyLogOptionsForReport(SITE_ID)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void missingSeverityFields_isEmptyForLowAndMedium() {
		assertThat(IncidentRules.missingSeverityFields(Severity.MEDIUM, null, null, null, null)).isEmpty();
		assertThat(IncidentRules.missingSeverityFields(null, null, null, null, null)).isEmpty();
		assertThat(IncidentRules.missingSeverityFields(Severity.HIGH, "x", "y", "z", false)).isEmpty();
	}

}
