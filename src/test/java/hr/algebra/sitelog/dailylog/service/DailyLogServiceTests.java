package hr.algebra.sitelog.dailylog.service;

import static hr.algebra.sitelog.support.TestEntities.NOW;
import static hr.algebra.sitelog.support.TestEntities.TODAY;
import static hr.algebra.sitelog.support.TestEntities.ZONE;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.exception.LogLockedException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.crew.domain.Crew;
import hr.algebra.sitelog.crew.repository.CrewRepository;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.domain.Weather;
import hr.algebra.sitelog.dailylog.domain.WorkEntry;
import hr.algebra.sitelog.dailylog.repository.DailyLogRepository;
import hr.algebra.sitelog.dailylog.repository.DailyLogSummary;
import hr.algebra.sitelog.dailylog.repository.LogReopenRequestRepository;
import hr.algebra.sitelog.incident.repository.IncidentRepository;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.site.repository.SiteRepository;
import hr.algebra.sitelog.support.TestEntities;
import hr.algebra.sitelog.user.domain.AppUser;
import hr.algebra.sitelog.user.repository.AppUserRepository;
import hr.algebra.sitelog.user.service.CurrentUserProvider;

/**
 * DailyLogService with mocked repositories and the real {@link DailyLogAssembler} (only its
 * {@link CrewRepository} is mocked), so the nested work entry / crew row rules run for real.
 */
@ExtendWith(MockitoExtension.class)
class DailyLogServiceTests {

	private static final Long LOG_ID = 10L;

	private static final Long SITE_ID = 1L;

	@Mock
	DailyLogRepository dailyLogs;

	@Mock
	SiteRepository sites;

	@Mock
	IncidentRepository incidents;

	@Mock
	LogReopenRequestRepository reopenRequests;

	@Mock
	AppUserRepository users;

	@Mock
	CurrentUserProvider currentUser;

	@Mock
	CrewRepository crews;

	DailyLogService service;

	Site site;

	Crew crewA;

	Crew crewB;

	@BeforeEach
	void setUp() {
		service = new DailyLogService(dailyLogs, sites, incidents, reopenRequests, users, currentUser,
				new DailyLogAssembler(crews), TestEntities.fixedClock());
		site = site(SITE_ID, "ZG-1", manager(7L));
		crewA = crew(1L, "Alpha", true);
		crewB = crew(2L, "Bravo", true);
	}

	private DailyLog logIn(LogStatus status, long version) {
		return log(LOG_ID, site, TODAY.minusDays(1), status, version);
	}

	/** A log with one work entry (id 20) that has one crew row (id 100, crew A, 8 h, 3 workers). */
	private DailyLog logWithEntry(LogStatus status, long version) {
		DailyLog log = logIn(status, version);
		WorkEntry entry = entry(20L, "Formwork");
		entry.setStartTime(LocalTime.of(7, 0));
		entry.addAssignment(assignment(100L, crewA, "8", 3));
		log.addWorkEntry(entry);
		return log;
	}

	private static DailyLogCommand command(LocalDate date, WorkEntryCommand... entries) {
		return new DailyLogCommand(date, Weather.RAIN, "  Wet morning ", List.of(entries));
	}

	private static WorkEntryCommand entryCommand(Long id, String description, CrewAssignmentCommand... rows) {
		return new WorkEntryCommand(id, description, null, LocalTime.of(7, 0), LocalTime.of(15, 0), List.of(rows));
	}

	private static CrewAssignmentCommand row(Long id, Long crewId, String hours) {
		return new CrewAssignmentCommand(id, crewId, new BigDecimal(hours), 2, null);
	}

	// ---------------------------------------------------------------- reads

	@Test
	void listForSite_delegatesToTheAggregateSummaryQuery() {
		PageRequest page = PageRequest.of(0, 10);
		PageImpl<DailyLogSummary> summaries = new PageImpl<>(List.of());
		when(dailyLogs.findSummariesBySiteId(SITE_ID, page)).thenReturn(summaries);

		assertThat(service.listForSite(SITE_ID, page)).isSameAs(summaries);
	}

	@Test
	void getDetail_ofUnlinkedDraft_isEditableDeletableAndCanBeSignedOff() {
		when(dailyLogs.findDetailById(LOG_ID)).thenReturn(Optional.of(logWithEntry(LogStatus.DRAFT, 2L)));
		when(incidents.existsByDailyLog_Id(LOG_ID)).thenReturn(false);
		when(reopenRequests.existsByDailyLog_Id(LOG_ID)).thenReturn(false);

		DailyLogDetails details = service.getDetail(LOG_ID);

		assertThat(details.siteCode()).isEqualTo("ZG-1");
		assertThat(details.version()).isEqualTo(2L);
		assertThat(details.editable()).isTrue();
		assertThat(details.deletable()).isTrue();
		assertThat(details.canSignOff()).isTrue();
		assertThat(details.totalHours()).isEqualByComparingTo("8");
		assertThat(details.crewCount()).isEqualTo(1L);
		assertThat(details.workEntries()).singleElement().satisfies(e -> {
			assertThat(e.description()).isEqualTo("Formwork");
			assertThat(e.totalWorkers()).isEqualTo(3);
		});
		assertThat(details.signedOffAt()).isNull();
		assertThat(details.signedOffByName()).isNull();
	}

	@Test
	void getDetail_ofDraftLinkedToIncident_isNotDeletable() {
		when(dailyLogs.findDetailById(LOG_ID)).thenReturn(Optional.of(logWithEntry(LogStatus.DRAFT, 0L)));
		when(incidents.existsByDailyLog_Id(LOG_ID)).thenReturn(true);

		assertThat(service.getDetail(LOG_ID).deletable()).isFalse();
	}

	@Test
	void getDetail_ofSignedOffLog_isLockedAndShowsSignOffInSitesTimeZone() {
		DailyLog log = logWithEntry(LogStatus.SIGNED_OFF, 4L);
		AppUser signer = manager(7L);
		log.setSignedOffBy(signer);
		log.setSignedOffAt(NOW);
		when(dailyLogs.findDetailById(LOG_ID)).thenReturn(Optional.of(log));

		DailyLogDetails details = service.getDetail(LOG_ID);

		assertThat(details.editable()).isFalse();
		assertThat(details.deletable()).isFalse();
		assertThat(details.canSignOff()).isFalse();
		assertThat(details.signedOffByName()).isEqualTo("Marko Kovacevic");
		assertThat(details.signedOffAt()).isEqualTo(NOW.atZone(ZONE));
		assertThat(details.signedOffAt().getHour()).isEqualTo(10);
	}

	@Test
	void getDetail_whenLogMissing_throwsNotFoundException() {
		when(dailyLogs.findDetailById(LOG_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getDetail(LOG_ID)).isInstanceOf(NotFoundException.class)
			.hasMessageContaining("DailyLog 10");
	}

	@Test
	void getForEdit_whenLogSignedOff_throwsLogLockedException() {
		when(dailyLogs.findDetailById(LOG_ID)).thenReturn(Optional.of(logWithEntry(LogStatus.SIGNED_OFF, 1L)));

		assertThatThrownBy(() -> service.getForEdit(LOG_ID)).isInstanceOf(LogLockedException.class);
	}

	@Test
	void getForEdit_whenLogReopened_returnsEditableDetails() {
		when(dailyLogs.findDetailById(LOG_ID)).thenReturn(Optional.of(logWithEntry(LogStatus.REOPENED, 1L)));

		DailyLogDetails details = service.getForEdit(LOG_ID);

		assertThat(details.status()).isEqualTo(LogStatus.REOPENED);
		assertThat(details.editable()).isTrue();
		assertThat(details.deletable()).isFalse();
	}

	@Test
	void getHeader_mapsLogAndSite() {
		when(dailyLogs.findWithSiteById(LOG_ID)).thenReturn(Optional.of(logIn(LogStatus.SIGNED_OFF, 5L)));

		DailyLogHeader header = service.getHeader(LOG_ID);

		assertThat(header.siteId()).isEqualTo(SITE_ID);
		assertThat(header.siteName()).isEqualTo("Site ZG-1");
		assertThat(header.version()).isEqualTo(5L);
		assertThat(header.editable()).isFalse();
	}

	@Test
	void getHeader_whenLogMissing_throwsNotFoundException() {
		when(dailyLogs.findWithSiteById(LOG_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getHeader(LOG_ID)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void getHeaderForChange_whenLogSignedOff_throwsLogLockedException() {
		when(dailyLogs.findWithSiteById(LOG_ID)).thenReturn(Optional.of(logIn(LogStatus.SIGNED_OFF, 5L)));

		assertThatThrownBy(() -> service.getHeaderForChange(LOG_ID)).isInstanceOf(LogLockedException.class);
	}

	@Test
	void getHeaderForChange_whenLogDraft_returnsHeader() {
		when(dailyLogs.findWithSiteById(LOG_ID)).thenReturn(Optional.of(logIn(LogStatus.DRAFT, 5L)));

		assertThat(service.getHeaderForChange(LOG_ID).editable()).isTrue();
	}

	// ---------------------------------------------------------------- create

	@Test
	void create_withEntriesAndCrewRows_savesWholeAggregateAsDraft() {
		when(sites.findById(SITE_ID)).thenReturn(Optional.of(site));
		when(dailyLogs.existsBySite_IdAndLogDate(SITE_ID, TODAY)).thenReturn(false);
		when(crews.findAllById(Set.of(1L, 2L))).thenReturn(List.of(crewA, crewB));
		when(dailyLogs.save(any(DailyLog.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 55L));

		Long id = service.create(SITE_ID,
				command(TODAY, entryCommand(null, "Formwork", row(null, 1L, "8"), row(null, 2L, "6.5"))));

		assertThat(id).isEqualTo(55L);
		ArgumentCaptor<DailyLog> saved = ArgumentCaptor.forClass(DailyLog.class);
		verify(dailyLogs).save(saved.capture());
		DailyLog log = saved.getValue();
		assertThat(log.getSite()).isSameAs(site);
		assertThat(log.getLogDate()).isEqualTo(TODAY);
		assertThat(log.getStatus()).isEqualTo(LogStatus.DRAFT);
		assertThat(log.getWeather()).isEqualTo(Weather.RAIN);
		assertThat(log.getNotes()).isEqualTo("Wet morning");
		assertThat(log.getWorkEntries()).singleElement().satisfies(entry -> {
			assertThat(entry.getDailyLog()).isSameAs(log);
			assertThat(entry.getAssignments()).extracting(a -> a.getCrew().getId()).containsExactlyInAnyOrder(1L, 2L);
		});
	}

	@Test
	void create_whenSiteMissing_throwsNotFoundException() {
		when(sites.findById(SITE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(SITE_ID, command(TODAY))).isInstanceOf(NotFoundException.class);
		verify(dailyLogs, never()).save(any());
	}

	@Test
	void create_whenDateIsTomorrow_throwsInvalidFieldOnLogDate() {
		when(sites.findById(SITE_ID)).thenReturn(Optional.of(site));

		assertThatThrownBy(() -> service.create(SITE_ID, command(TODAY.plusDays(1))))
			.isInstanceOfSatisfying(InvalidFieldException.class, e -> {
				assertThat(e.getField()).isEqualTo("logDate");
				assertThat(e.getMessageKey()).isEqualTo(DailyLogService.DATE_FUTURE);
			});
		verify(dailyLogs, never()).save(any());
	}

	@Test
	void create_whenSiteAlreadyHasALogForThatDate_throwsInvalidFieldOnLogDate() {
		when(sites.findById(SITE_ID)).thenReturn(Optional.of(site));
		when(dailyLogs.existsBySite_IdAndLogDate(SITE_ID, TODAY.minusDays(3))).thenReturn(true);

		assertThatThrownBy(() -> service.create(SITE_ID, command(TODAY.minusDays(3))))
			.isInstanceOfSatisfying(InvalidFieldException.class, e -> {
				assertThat(e.getField()).isEqualTo("logDate");
				assertThat(e.getMessageKey()).isEqualTo(DailyLogService.DATE_DUPLICATE);
			});
		verify(dailyLogs, never()).save(any());
	}

	@Test
	void create_withInactiveCrewOnARow_throwsInvalidFieldWithTheNestedRowPath() {
		Crew retired = crew(3L, "Retired", false);
		when(sites.findById(SITE_ID)).thenReturn(Optional.of(site));
		when(dailyLogs.existsBySite_IdAndLogDate(SITE_ID, TODAY)).thenReturn(false);
		when(crews.findAllById(Set.of(1L, 3L))).thenReturn(List.of(crewA, retired));

		assertThatThrownBy(() -> service.create(SITE_ID,
				command(TODAY, entryCommand(null, "Formwork", row(null, 1L, "8"), row(null, 3L, "2")))))
			.isInstanceOfSatisfying(InvalidFieldException.class, e -> {
				assertThat(e.getField()).isEqualTo("workEntries[0].crewAssignments[1].crewId");
				assertThat(e.getMessageKey()).isEqualTo(DailyLogAssembler.CREW_INVALID);
			});
		verify(dailyLogs, never()).save(any());
	}

	@Test
	void create_withSameCrewTwiceOnAnEntry_throwsInvalidFieldDuplicate() {
		when(sites.findById(SITE_ID)).thenReturn(Optional.of(site));
		when(dailyLogs.existsBySite_IdAndLogDate(SITE_ID, TODAY)).thenReturn(false);
		when(crews.findAllById(Set.of(1L))).thenReturn(List.of(crewA));

		assertThatThrownBy(() -> service.create(SITE_ID,
				command(TODAY, entryCommand(null, "Formwork", row(null, 1L, "8"), row(null, 1L, "2")))))
			.isInstanceOfSatisfying(InvalidFieldException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(DailyLogAssembler.CREW_DUPLICATE));
	}

	// ---------------------------------------------------------------- update

	@Test
	void update_loadsTheRootWithForceIncrementBeforeTheDetailQuery() {
		DailyLog log = logWithEntry(LogStatus.DRAFT, 3L);
		when(dailyLogs.findForChildUpdateById(LOG_ID)).thenReturn(Optional.of(log));
		when(dailyLogs.findDetailById(LOG_ID)).thenReturn(Optional.of(log));
		when(dailyLogs.existsBySite_IdAndLogDateAndIdNot(SITE_ID, TODAY.minusDays(1), LOG_ID)).thenReturn(false);
		when(crews.findAllById(Set.of(1L))).thenReturn(List.of(crewA));

		service.update(LOG_ID, 3L, command(TODAY.minusDays(1), entryCommand(20L, "Formwork", row(100L, 1L, "9"))));

		InOrder order = inOrder(dailyLogs);
		order.verify(dailyLogs).findForChildUpdateById(LOG_ID);
		order.verify(dailyLogs).findDetailById(LOG_ID);
		assertThat(log.getWorkEntries()).singleElement()
			.satisfies(e -> assertThat(e.getAssignments()).singleElement()
				.satisfies(a -> assertThat(a.getHoursWorked()).isEqualByComparingTo("9")));
	}

	@Test
	void update_withNewDateWeatherAndChildren_synchronisesTheAggregate() {
		DailyLog log = logWithEntry(LogStatus.DRAFT, 3L);
		WorkEntry second = entry(21L, "Cleanup");
		log.addWorkEntry(second);
		when(dailyLogs.findForChildUpdateById(LOG_ID)).thenReturn(Optional.of(log));
		when(dailyLogs.existsBySite_IdAndLogDateAndIdNot(SITE_ID, TODAY.minusDays(2), LOG_ID)).thenReturn(false);
		when(crews.findAllById(Set.of(1L, 2L))).thenReturn(List.of(crewA, crewB));

		service.update(LOG_ID, 3L, command(TODAY.minusDays(2), entryCommand(20L, "Formwork L2", row(100L, 1L, "8")),
				entryCommand(null, "Rebar", row(null, 2L, "5"))));

		assertThat(log.getLogDate()).isEqualTo(TODAY.minusDays(2));
		assertThat(log.getWeather()).isEqualTo(Weather.RAIN);
		assertThat(log.getNotes()).isEqualTo("Wet morning");
		assertThat(log.getWorkEntries()).extracting(WorkEntry::getDescription)
			.containsExactlyInAnyOrder("Formwork L2", "Rebar");
		assertThat(second.getDailyLog()).isNull();
	}

	@Test
	void update_whenFormVersionIsStale_throwsOptimisticLockingFailureAndChangesNothing() {
		DailyLog log = logWithEntry(LogStatus.DRAFT, 4L);
		when(dailyLogs.findForChildUpdateById(LOG_ID)).thenReturn(Optional.of(log));

		assertThatThrownBy(() -> service.update(LOG_ID, 3L, command(TODAY, entryCommand(20L, "Changed"))))
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
		assertThat(log.getLogDate()).isEqualTo(TODAY.minusDays(1));
		assertThat(log.getWorkEntries()).extracting(WorkEntry::getDescription).containsExactly("Formwork");
	}

	@Test
	void update_whenLogSignedOff_throwsLogLockedExceptionAndChangesNothing() {
		DailyLog log = logWithEntry(LogStatus.SIGNED_OFF, 3L);
		when(dailyLogs.findForChildUpdateById(LOG_ID)).thenReturn(Optional.of(log));

		assertThatThrownBy(() -> service.update(LOG_ID, 3L, command(TODAY, entryCommand(20L, "Changed"))))
			.isInstanceOfSatisfying(LogLockedException.class, e -> assertThat(e.getDailyLogId()).isEqualTo(LOG_ID));
		assertThat(log.getNotes()).isNull();
		assertThat(log.getWorkEntries()).extracting(WorkEntry::getDescription).containsExactly("Formwork");
		verify(dailyLogs, never()).existsBySite_IdAndLogDateAndIdNot(anyLong(), any(), anyLong());
	}

	@Test
	void update_whenLogReopened_isAllowed() {
		DailyLog log = logWithEntry(LogStatus.REOPENED, 3L);
		when(dailyLogs.findForChildUpdateById(LOG_ID)).thenReturn(Optional.of(log));
		when(dailyLogs.existsBySite_IdAndLogDateAndIdNot(SITE_ID, TODAY, LOG_ID)).thenReturn(false);
		when(crews.findAllById(Set.of(1L))).thenReturn(List.of(crewA));

		service.update(LOG_ID, 3L, command(TODAY, entryCommand(20L, "Corrected", row(100L, 1L, "8"))));

		assertThat(log.getWorkEntries()).extracting(WorkEntry::getDescription).containsExactly("Corrected");
	}

	@Test
	void update_whenDateIsInTheFuture_throwsInvalidFieldOnLogDate() {
		when(dailyLogs.findForChildUpdateById(LOG_ID)).thenReturn(Optional.of(logWithEntry(LogStatus.DRAFT, 3L)));

		assertThatThrownBy(() -> service.update(LOG_ID, 3L, command(TODAY.plusDays(1))))
			.isInstanceOfSatisfying(InvalidFieldException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(DailyLogService.DATE_FUTURE));
	}

	@Test
	void update_whenNewDateBelongsToAnotherLogOfTheSite_throwsInvalidFieldDuplicate() {
		DailyLog log = logWithEntry(LogStatus.DRAFT, 3L);
		when(dailyLogs.findForChildUpdateById(LOG_ID)).thenReturn(Optional.of(log));
		when(dailyLogs.existsBySite_IdAndLogDateAndIdNot(SITE_ID, TODAY.minusDays(5), LOG_ID)).thenReturn(true);

		assertThatThrownBy(() -> service.update(LOG_ID, 3L, command(TODAY.minusDays(5))))
			.isInstanceOfSatisfying(InvalidFieldException.class, e -> {
				assertThat(e.getField()).isEqualTo("logDate");
				assertThat(e.getMessageKey()).isEqualTo(DailyLogService.DATE_DUPLICATE);
			});
		assertThat(log.getLogDate()).isEqualTo(TODAY.minusDays(1));
	}

	@Test
	void update_whenLogMissing_throwsNotFoundException() {
		when(dailyLogs.findForChildUpdateById(LOG_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(LOG_ID, 0L, command(TODAY))).isInstanceOf(NotFoundException.class);
	}

	// ---------------------------------------------------------------- delete

	@Test
	void delete_ofUnlinkedDraft_deletesItAndReturnsSiteIdForTheRedirect() {
		DailyLog log = logWithEntry(LogStatus.DRAFT, 2L);
		when(dailyLogs.findDetailById(LOG_ID)).thenReturn(Optional.of(log));
		when(incidents.existsByDailyLog_Id(LOG_ID)).thenReturn(false);
		when(reopenRequests.existsByDailyLog_Id(LOG_ID)).thenReturn(false);

		Long siteId = service.delete(LOG_ID, 2L);

		assertThat(siteId).isEqualTo(SITE_ID);
		verify(dailyLogs).delete(log);
	}

	@Test
	void delete_whenLogSignedOff_throwsLogLockedException() {
		when(dailyLogs.findDetailById(LOG_ID)).thenReturn(Optional.of(logWithEntry(LogStatus.SIGNED_OFF, 2L)));

		assertThatThrownBy(() -> service.delete(LOG_ID, 2L)).isInstanceOf(LogLockedException.class);
		verify(dailyLogs, never()).delete(any());
	}

	@Test
	void delete_whenLogReopened_throwsDeletionNotAllowedBecauseOnlyDraftsCanBeDeleted() {
		when(dailyLogs.findDetailById(LOG_ID)).thenReturn(Optional.of(logWithEntry(LogStatus.REOPENED, 2L)));

		assertThatThrownBy(() -> service.delete(LOG_ID, 2L))
			.isInstanceOfSatisfying(DeletionNotAllowedException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(DailyLogService.DELETE_NOT_DRAFT));
		verify(dailyLogs, never()).delete(any());
	}

	@Test
	void delete_whenDraftIsLinkedToAnIncident_throwsDeletionNotAllowedLinked() {
		when(dailyLogs.findDetailById(LOG_ID)).thenReturn(Optional.of(logWithEntry(LogStatus.DRAFT, 2L)));
		when(incidents.existsByDailyLog_Id(LOG_ID)).thenReturn(true);

		assertDeleteLinked();
	}

	@Test
	void delete_whenDraftHasAReopenRequest_throwsDeletionNotAllowedLinked() {
		when(dailyLogs.findDetailById(LOG_ID)).thenReturn(Optional.of(logWithEntry(LogStatus.DRAFT, 2L)));
		when(incidents.existsByDailyLog_Id(LOG_ID)).thenReturn(false);
		when(reopenRequests.existsByDailyLog_Id(LOG_ID)).thenReturn(true);

		assertDeleteLinked();
	}

	private void assertDeleteLinked() {
		assertThatThrownBy(() -> service.delete(LOG_ID, 2L))
			.isInstanceOfSatisfying(DeletionNotAllowedException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(DailyLogService.DELETE_LINKED));
		verify(dailyLogs, never()).delete(any());
	}

	@Test
	void delete_whenFormVersionIsStale_throwsOptimisticLockingFailure() {
		when(dailyLogs.findDetailById(LOG_ID)).thenReturn(Optional.of(logWithEntry(LogStatus.DRAFT, 3L)));

		assertThatThrownBy(() -> service.delete(LOG_ID, 2L)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
		verify(dailyLogs, never()).delete(any());
	}

	@Test
	void delete_whenLogMissing_throwsNotFoundException() {
		when(dailyLogs.findDetailById(LOG_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete(LOG_ID, 0L)).isInstanceOf(NotFoundException.class);
	}

	// ---------------------------------------------------------------- sign-off

	@Test
	void signOff_ofDraftWithWorkEntry_locksLogAndRecordsWhoAndWhen() {
		DailyLog log = logWithEntry(LogStatus.DRAFT, 2L);
		AppUser signer = manager(7L);
		when(dailyLogs.findDetailById(LOG_ID)).thenReturn(Optional.of(log));
		when(currentUser.currentUserId()).thenReturn(7L);
		when(users.getReferenceById(7L)).thenReturn(signer);

		service.signOff(LOG_ID, 2L);

		assertThat(log.getStatus()).isEqualTo(LogStatus.SIGNED_OFF);
		assertThat(log.getSignedOffBy()).isSameAs(signer);
		assertThat(log.getSignedOffAt()).isEqualTo(NOW);
		assertThat(log.getStatus().isEditable()).isFalse();
	}

	@Test
	void signOff_ofReopenedLog_signsItOffAgain() {
		DailyLog log = logWithEntry(LogStatus.REOPENED, 6L);
		when(dailyLogs.findDetailById(LOG_ID)).thenReturn(Optional.of(log));
		when(currentUser.currentUserId()).thenReturn(7L);
		when(users.getReferenceById(7L)).thenReturn(manager(7L));

		service.signOff(LOG_ID, 6L);

		assertThat(log.getStatus()).isEqualTo(LogStatus.SIGNED_OFF);
	}

	@Test
	void signOff_whenLogAlreadySignedOff_throwsInvalidTransitionException() {
		DailyLog log = logWithEntry(LogStatus.SIGNED_OFF, 2L);
		when(dailyLogs.findDetailById(LOG_ID)).thenReturn(Optional.of(log));

		assertThatThrownBy(() -> service.signOff(LOG_ID, 2L))
			.isInstanceOfSatisfying(InvalidTransitionException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(DailyLogService.ALREADY_SIGNED_OFF));
		assertThat(log.getSignedOffAt()).isNull();
	}

	@Test
	void signOff_whenLogHasNoWorkEntries_throwsInvalidTransitionAndStaysDraft() {
		DailyLog log = logIn(LogStatus.DRAFT, 2L);
		when(dailyLogs.findDetailById(LOG_ID)).thenReturn(Optional.of(log));

		assertThatThrownBy(() -> service.signOff(LOG_ID, 2L))
			.isInstanceOfSatisfying(InvalidTransitionException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(DailyLogService.SIGN_OFF_EMPTY));
		assertThat(log.getStatus()).isEqualTo(LogStatus.DRAFT);
		assertThat(log.getSignedOffBy()).isNull();
	}

	@Test
	void signOff_whenFormVersionIsStale_throwsOptimisticLockingFailureAndStaysDraft() {
		DailyLog log = logWithEntry(LogStatus.DRAFT, 3L);
		when(dailyLogs.findDetailById(LOG_ID)).thenReturn(Optional.of(log));

		assertThatThrownBy(() -> service.signOff(LOG_ID, 2L)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
		assertThat(log.getStatus()).isEqualTo(LogStatus.DRAFT);
	}

	@Test
	void signOff_whenLogMissing_throwsNotFoundException() {
		when(dailyLogs.findDetailById(LOG_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.signOff(LOG_ID, 0L)).isInstanceOf(NotFoundException.class);
	}

}
