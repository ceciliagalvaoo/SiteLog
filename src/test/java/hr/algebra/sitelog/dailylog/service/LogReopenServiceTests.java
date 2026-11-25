package hr.algebra.sitelog.dailylog.service;

import static hr.algebra.sitelog.support.TestEntities.NOW;
import static hr.algebra.sitelog.support.TestEntities.TODAY;
import static hr.algebra.sitelog.support.TestEntities.log;
import static hr.algebra.sitelog.support.TestEntities.manager;
import static hr.algebra.sitelog.support.TestEntities.site;
import static hr.algebra.sitelog.support.TestEntities.user;
import static hr.algebra.sitelog.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.LogReopenRequest;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.domain.ReopenStatus;
import hr.algebra.sitelog.dailylog.repository.DailyLogRepository;
import hr.algebra.sitelog.dailylog.repository.LogReopenRequestRepository;
import hr.algebra.sitelog.notification.domain.NotificationType;
import hr.algebra.sitelog.notification.service.NotificationPublisher;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.support.TestEntities;
import hr.algebra.sitelog.user.domain.AppUser;
import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.repository.AppUserRepository;
import hr.algebra.sitelog.user.service.CurrentUser;
import hr.algebra.sitelog.user.service.CurrentUserProvider;

/** AR1 reopen workflow: request with reason, supervisor approval / rejection, the state of the log. */
@ExtendWith(MockitoExtension.class)
class LogReopenServiceTests {

	private static final Long LOG_ID = 10L;

	private static final Long REQUEST_ID = 50L;

	@Mock
	LogReopenRequestRepository reopenRequests;

	@Mock
	DailyLogRepository dailyLogs;

	@Mock
	AppUserRepository users;

	@Mock
	CurrentUserProvider currentUser;

	@Mock
	NotificationPublisher notifications;

	private LogReopenService service;

	private final AppUser manager = manager(7L);

	private final AppUser supervisor = user(9L, "Ivana Babic", RoleName.SAFETY_SUPERVISOR, true);

	private final Site site = site(1L, "ZG-01", manager);

	@BeforeEach
	void setUp() {
		service = new LogReopenService(reopenRequests, dailyLogs, users, currentUser, notifications,
				TestEntities.fixedClock());
	}

	private DailyLog signedLog() {
		DailyLog log = log(LOG_ID, site, TODAY.minusDays(1), LogStatus.SIGNED_OFF, 3);
		log.setRevision(0);
		return log;
	}

	private LogReopenRequest pending(DailyLog log) {
		return withId(new LogReopenRequest(log, "Wrong hours", manager, NOW.minusSeconds(3600)), REQUEST_ID);
	}

	private void signedInAs(AppUser user) {
		CurrentUser current = CurrentUser.of(user);
		org.mockito.Mockito.lenient().when(currentUser.currentUser()).thenReturn(current);
		org.mockito.Mockito.lenient().when(currentUser.currentUserId()).thenReturn(user.getId());
		org.mockito.Mockito.lenient().when(users.getReferenceById(user.getId())).thenReturn(user);
	}

	// ------------------------------------------------------------------ request

	@Test
	void request_savesThePendingRequestAndNotifiesTheSupervisors() {
		signedInAs(manager);
		when(dailyLogs.findWithSiteById(LOG_ID)).thenReturn(Optional.of(signedLog()));
		when(reopenRequests.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), REQUEST_ID));

		Long id = service.request(LOG_ID, 3L, "  Hours recorded for the wrong crew  ");

		assertThat(id).isEqualTo(REQUEST_ID);
		ArgumentCaptor<LogReopenRequest> saved = ArgumentCaptor.forClass(LogReopenRequest.class);
		verify(reopenRequests).save(saved.capture());
		assertThat(saved.getValue().getReason()).isEqualTo("Hours recorded for the wrong crew");
		assertThat(saved.getValue().getStatus()).isEqualTo(ReopenStatus.PENDING);
		assertThat(saved.getValue().getRequestedBy()).isSameAs(manager);
		assertThat(saved.getValue().getRequestedAt()).isEqualTo(NOW);
		verify(notifications).notifyRole(RoleName.SAFETY_SUPERVISOR, NotificationType.REOPEN_REQUESTED,
				"/daily-logs/10", "ZG-01", TODAY.minusDays(1), "Marko Kovacevic");
	}

	@Test
	void request_withoutReason_isAFieldErrorAndNothingIsSaved() {
		when(dailyLogs.findWithSiteById(LOG_ID)).thenReturn(Optional.of(signedLog()));

		assertThatThrownBy(() -> service.request(LOG_ID, 3L, "   "))
			.isInstanceOfSatisfying(InvalidFieldException.class, e -> {
				assertThat(e.getField()).isEqualTo("reason");
				assertThat(e.getMessageKey()).isEqualTo(LogReopenService.REASON_REQUIRED);
			});
		verify(reopenRequests, never()).save(any());
		verifyNoInteractions(notifications);
	}

	@Test
	void request_onALogThatIsNotSignedOff_isAnInvalidTransition() {
		when(dailyLogs.findWithSiteById(LOG_ID))
			.thenReturn(Optional.of(log(LOG_ID, site, TODAY, LogStatus.DRAFT, 3)));

		assertThatThrownBy(() -> service.request(LOG_ID, 3L, "reason"))
			.isInstanceOfSatisfying(InvalidTransitionException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(LogReopenService.NOT_SIGNED_OFF));
	}

	@Test
	void request_whenARequestIsAlreadyPending_isRefused() {
		when(dailyLogs.findWithSiteById(LOG_ID)).thenReturn(Optional.of(signedLog()));
		when(reopenRequests.existsByDailyLog_IdAndStatus(LOG_ID, ReopenStatus.PENDING)).thenReturn(true);

		assertThatThrownBy(() -> service.request(LOG_ID, 3L, "reason"))
			.isInstanceOfSatisfying(InvalidTransitionException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(LogReopenService.ALREADY_PENDING));
	}

	@Test
	void request_withAStaleVersion_isAnOptimisticLockConflict() {
		when(dailyLogs.findWithSiteById(LOG_ID)).thenReturn(Optional.of(signedLog()));

		assertThatThrownBy(() -> service.request(LOG_ID, 2L, "reason"))
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

	@Test
	void request_forAnUnknownLog_isNotFound() {
		when(dailyLogs.findWithSiteById(LOG_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.request(LOG_ID, 3L, "reason")).isInstanceOf(NotFoundException.class);
	}

	// ------------------------------------------------------------------ approve

	@Test
	void approve_reopensTheLogWithTheNextRevisionAndRecordsTheDecision() {
		signedInAs(supervisor);
		DailyLog log = signedLog();
		LogReopenRequest request = pending(log);
		when(reopenRequests.findForDecisionById(REQUEST_ID)).thenReturn(Optional.of(request));
		when(dailyLogs.findById(LOG_ID)).thenReturn(Optional.of(log));

		service.approve(LOG_ID, REQUEST_ID, "  ");

		assertThat(log.getStatus()).isEqualTo(LogStatus.REOPENED);
		assertThat(log.getRevision()).isEqualTo(1);
		assertThat(log.getStatus().isEditable()).isTrue();
		assertThat(request.getStatus()).isEqualTo(ReopenStatus.APPROVED);
		assertThat(request.getApprovedBy()).isSameAs(supervisor);
		assertThat(request.getDecidedAt()).isEqualTo(NOW);
		assertThat(request.getDecisionNote()).isNull();
	}

	@Test
	void approve_keepsTheOptionalNote() {
		signedInAs(supervisor);
		DailyLog log = signedLog();
		LogReopenRequest request = pending(log);
		when(reopenRequests.findForDecisionById(REQUEST_ID)).thenReturn(Optional.of(request));
		when(dailyLogs.findById(LOG_ID)).thenReturn(Optional.of(log));

		service.approve(LOG_ID, REQUEST_ID, " Correct only the crew hours ");

		assertThat(request.getDecisionNote()).isEqualTo("Correct only the crew hours");
	}

	@Test
	void approve_ofARequestThatIsAlreadyDecided_isRefusedAndTheLogStaysLocked() {
		DailyLog log = signedLog();
		LogReopenRequest request = pending(log);
		request.decide(ReopenStatus.REJECTED, supervisor, NOW, "No");
		when(reopenRequests.findForDecisionById(REQUEST_ID)).thenReturn(Optional.of(request));

		assertThatThrownBy(() -> service.approve(LOG_ID, REQUEST_ID, null))
			.isInstanceOfSatisfying(InvalidTransitionException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(LogReopenService.ALREADY_DECIDED));
		assertThat(log.getStatus()).isEqualTo(LogStatus.SIGNED_OFF);
	}

	@Test
	void approve_ofARequestOfAnotherLog_isNotFound() {
		DailyLog other = log(99L, site, TODAY, LogStatus.SIGNED_OFF, 1);
		when(reopenRequests.findForDecisionById(REQUEST_ID)).thenReturn(Optional.of(pending(other)));

		assertThatThrownBy(() -> service.approve(LOG_ID, REQUEST_ID, null)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void approve_whenTheLogIsNoLongerSignedOff_isRefused() {
		DailyLog log = log(LOG_ID, site, TODAY, LogStatus.REOPENED, 4);
		when(reopenRequests.findForDecisionById(REQUEST_ID)).thenReturn(Optional.of(pending(log)));
		when(dailyLogs.findById(LOG_ID)).thenReturn(Optional.of(log));

		assertThatThrownBy(() -> service.approve(LOG_ID, REQUEST_ID, null))
			.isInstanceOf(InvalidTransitionException.class);
	}

	// ------------------------------------------------------------------ reject

	@Test
	void reject_recordsTheReasonAndLeavesTheLogSignedOff() {
		signedInAs(supervisor);
		DailyLog log = signedLog();
		LogReopenRequest request = pending(log);
		when(reopenRequests.findForDecisionById(REQUEST_ID)).thenReturn(Optional.of(request));

		service.reject(LOG_ID, REQUEST_ID, " Add it to today's log instead ");

		assertThat(request.getStatus()).isEqualTo(ReopenStatus.REJECTED);
		assertThat(request.getDecisionNote()).isEqualTo("Add it to today's log instead");
		assertThat(request.getApprovedBy()).isSameAs(supervisor);
		assertThat(log.getStatus()).isEqualTo(LogStatus.SIGNED_OFF);
		assertThat(log.getRevision()).isZero();
	}

	@Test
	void reject_withoutReason_isAFieldErrorBeforeAnythingIsLoaded() {
		assertThatThrownBy(() -> service.reject(LOG_ID, REQUEST_ID, null))
			.isInstanceOfSatisfying(InvalidFieldException.class,
					e -> assertThat(e.getMessageKey()).isEqualTo(LogReopenService.REJECT_REASON_REQUIRED));
		verify(reopenRequests, never()).findForDecisionById(any());
	}

	// ------------------------------------------------------------------ reads

	@Test
	void panel_offersARequestOnlyForASignedOffLogWithoutPendingRequest() {
		DailyLog log = signedLog();
		LogReopenRequest decided = withId(new LogReopenRequest(log, "Old", manager, NOW.minusSeconds(7200)), 1L);
		decided.decide(ReopenStatus.REJECTED, supervisor, NOW.minusSeconds(3600), "No");
		when(dailyLogs.findById(LOG_ID)).thenReturn(Optional.of(log));
		when(reopenRequests.findByDailyLog_IdOrderByRequestedAtDesc(LOG_ID)).thenReturn(List.of(decided));

		ReopenPanel panel = service.panel(LOG_ID);

		assertThat(panel.canRequest()).isTrue();
		assertThat(panel.hasPending()).isFalse();
		assertThat(panel.history()).singleElement().satisfies(view -> {
			assertThat(view.status()).isEqualTo(ReopenStatus.REJECTED);
			assertThat(view.decidedByName()).isEqualTo("Ivana Babic");
			assertThat(view.decisionNote()).isEqualTo("No");
			assertThat(view.requestedAt().getZone()).isEqualTo(TestEntities.ZONE);
		});
	}

	@Test
	void panel_showsThePendingRequestAndOffersNoNewOne() {
		DailyLog log = signedLog();
		when(dailyLogs.findById(LOG_ID)).thenReturn(Optional.of(log));
		when(reopenRequests.findByDailyLog_IdOrderByRequestedAtDesc(LOG_ID)).thenReturn(List.of(pending(log)));

		ReopenPanel panel = service.panel(LOG_ID);

		assertThat(panel.hasPending()).isTrue();
		assertThat(panel.pending().id()).isEqualTo(REQUEST_ID);
		assertThat(panel.pending().decidedByName()).isNull();
		assertThat(panel.canRequest()).isFalse();
	}

	@Test
	void panel_ofAnUnknownLog_isNotFound() {
		when(dailyLogs.findById(LOG_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.panel(LOG_ID)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void pendingRequests_listsTheQueueWithSiteAndRequester() {
		DailyLog log = signedLog();
		when(reopenRequests.findByStatusOrderByRequestedAtAsc(ReopenStatus.PENDING)).thenReturn(List.of(pending(log)));

		assertThat(service.pendingRequests()).singleElement().satisfies(item -> {
			assertThat(item.requestId()).isEqualTo(REQUEST_ID);
			assertThat(item.dailyLogId()).isEqualTo(LOG_ID);
			assertThat(item.siteCode()).isEqualTo("ZG-01");
			assertThat(item.requestedByName()).isEqualTo("Marko Kovacevic");
			assertThat(item.requestedAt().toInstant()).isEqualTo(Instant.from(NOW.minusSeconds(3600)));
		});
		verify(reopenRequests).findByStatusOrderByRequestedAtAsc(eq(ReopenStatus.PENDING));
	}

}
