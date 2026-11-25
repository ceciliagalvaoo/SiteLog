package hr.algebra.sitelog.dailylog.web;

import static hr.algebra.sitelog.support.TestEntities.NOW;
import static hr.algebra.sitelog.support.TestEntities.TODAY;
import static hr.algebra.sitelog.support.TestEntities.ZONE;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import hr.algebra.sitelog.audit.domain.DomainActionType;
import hr.algebra.sitelog.audit.service.DomainActionView;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.web.Flash;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.domain.ReopenStatus;
import hr.algebra.sitelog.dailylog.domain.Weather;
import hr.algebra.sitelog.dailylog.service.DailyLogDetails;
import hr.algebra.sitelog.dailylog.service.DailyLogHeader;
import hr.algebra.sitelog.dailylog.service.DailyLogHistory;
import hr.algebra.sitelog.dailylog.service.DailyLogHistoryService;
import hr.algebra.sitelog.dailylog.service.DailyLogService;
import hr.algebra.sitelog.dailylog.service.EntityChange;
import hr.algebra.sitelog.dailylog.service.FieldChange;
import hr.algebra.sitelog.dailylog.service.LogReopenService;
import hr.algebra.sitelog.dailylog.service.PendingReopenItem;
import hr.algebra.sitelog.dailylog.service.ReopenPanel;
import hr.algebra.sitelog.dailylog.service.ReopenRequestView;
import hr.algebra.sitelog.dailylog.service.RevisionView;
import hr.algebra.sitelog.site.service.SiteAccess;
import hr.algebra.sitelog.support.WebSliceConfig;
import hr.algebra.sitelog.support.WebSliceTest;

/** AR1 screens: reopen request / approve / reject (modals on the log detail), the queue and the history. */
@WebMvcTest(LogReopenController.class)
@Import(WebSliceConfig.class)
@WithMockUser(roles = "SAFETY_SUPERVISOR")
class LogReopenControllerTests extends WebSliceTest {

	private static final Long LOG_ID = 10L;

	@MockitoBean
	LogReopenService reopenService;

	@MockitoBean
	DailyLogService dailyLogService;

	@MockitoBean
	DailyLogHistoryService historyService;

	/** The detail template asks {@code @siteAccess} for a site manager (not part of the slice). */
	@MockitoBean(name = "siteAccess")
	SiteAccess siteAccess;

	private final ReopenRequestView pending = new ReopenRequestView(50L, ReopenStatus.PENDING, "Wrong hours",
			"Marko Kovacevic", NOW.atZone(ZONE), null, null, null);

	@BeforeEach
	void detail() {
		when(dailyLogService.getDetail(LOG_ID)).thenReturn(new DailyLogDetails(LOG_ID, 1L, "ZG-01", "Tower A",
				TODAY.minusDays(1), LogStatus.SIGNED_OFF, 0, Weather.SUNNY, null, "Marko Kovacevic", NOW.atZone(ZONE),
				3L, false, false, List.of()));
		when(reopenService.panel(LOG_ID))
			.thenReturn(new ReopenPanel(LOG_ID, LogStatus.SIGNED_OFF, pending, List.of(pending), false));
	}

	@Test
	@WithMockUser(roles = "SITE_MANAGER")
	void request_withReason_redirectsToTheLogWithAFlash() throws Exception {
		mvc.perform(post("/daily-logs/10/reopen-requests").with(csrf()).param("reason", "Wrong hours")
			.param("version", "3"))
			.andExpect(redirectedUrl("/daily-logs/10"))
			.andExpect(flash().attribute(Flash.SUCCESS, "reopen.flash.requested"));
		verify(reopenService).request(LOG_ID, 3L, "Wrong hours");
	}

	@Test
	@WithMockUser(roles = "SITE_MANAGER")
	void request_withoutReason_reRendersTheDetailWithTheModalOpen() throws Exception {
		mvc.perform(post("/daily-logs/10/reopen-requests").with(csrf()).param("reason", " ").param("version", "3"))
			.andExpect(status().isOk())
			.andExpect(view().name(DailyLogController.DETAIL_VIEW))
			.andExpect(model().attribute("reopenModal", true))
			.andExpect(model().attributeHasFieldErrors(DailyLogController.REOPEN_FORM, "reason"));
		verify(reopenService, never()).request(any(), any(), any());
	}

	@Test
	@WithMockUser(roles = "SITE_MANAGER")
	void request_serviceFieldError_landsOnTheReason() throws Exception {
		when(reopenService.request(LOG_ID, 3L, "x"))
			.thenThrow(new InvalidFieldException("reason", LogReopenService.REASON_REQUIRED));

		mvc.perform(post("/daily-logs/10/reopen-requests").with(csrf()).param("reason", "x").param("version", "3"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode(DailyLogController.REOPEN_FORM, "reason",
					LogReopenService.REASON_REQUIRED));
	}

	@Test
	void approve_redirectsWithAFlash() throws Exception {
		mvc.perform(post("/daily-logs/10/reopen-requests/50/approve").with(csrf()))
			.andExpect(redirectedUrl("/daily-logs/10"))
			.andExpect(flash().attribute(Flash.SUCCESS, "reopen.flash.approved"));
		verify(reopenService).approve(LOG_ID, 50L, null);
	}

	@Test
	@WithMockUser(roles = "SITE_MANAGER")
	void approve_byASiteManager_is403() throws Exception {
		doThrow(new AccessDeniedException("supervisor only")).when(reopenService).approve(LOG_ID, 50L, null);

		mvc.perform(post("/daily-logs/10/reopen-requests/50/approve").with(csrf())).andExpect(status().isForbidden());
	}

	@Test
	void approve_ofADecidedRequest_redirectsBackWithTheError() throws Exception {
		doThrow(new InvalidTransitionException(LogReopenService.ALREADY_DECIDED, "decided")).when(reopenService)
			.approve(LOG_ID, 50L, null);

		mvc.perform(post("/daily-logs/10/reopen-requests/50/approve").with(csrf())
			.header("Referer", "http://localhost/daily-logs/10"))
			.andExpect(redirectedUrl("/daily-logs/10"))
			.andExpect(flash().attribute(Flash.ERROR, LogReopenService.ALREADY_DECIDED));
	}

	@Test
	void reject_withReason_redirectsWithAFlash() throws Exception {
		mvc.perform(post("/daily-logs/10/reopen-requests/50/reject").with(csrf()).param("reason", "Use today's log"))
			.andExpect(redirectedUrl("/daily-logs/10"))
			.andExpect(flash().attribute(Flash.SUCCESS, "reopen.flash.rejected"));
		verify(reopenService).reject(LOG_ID, 50L, "Use today's log");
	}

	@Test
	void reject_withoutReason_reRendersTheDetailWithTheRejectModalOpen() throws Exception {
		mvc.perform(post("/daily-logs/10/reopen-requests/50/reject").with(csrf()).param("reason", ""))
			.andExpect(status().isOk())
			.andExpect(model().attribute("rejectModal", true))
			.andExpect(model().attributeHasFieldErrors(DailyLogController.REJECT_FORM, "reason"))
			.andExpect(content().string(containsString("reject-modal")))
			.andExpect(content().string(containsString("A reason is required to reject a reopen request")));
	}

	@Test
	void reject_serviceFieldError_landsOnTheReason() throws Exception {
		doThrow(new InvalidFieldException("reason", LogReopenService.REJECT_REASON_REQUIRED)).when(reopenService)
			.reject(LOG_ID, 50L, "x");

		mvc.perform(post("/daily-logs/10/reopen-requests/50/reject").with(csrf()).param("reason", "x"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode(DailyLogController.REJECT_FORM, "reason",
					LogReopenService.REJECT_REASON_REQUIRED));
	}

	@Test
	void queue_listsThePendingRequests() throws Exception {
		when(reopenService.pendingRequests()).thenReturn(List.of(new PendingReopenItem(50L, LOG_ID, "ST-01", "Split",
				TODAY.minusDays(1), "Worker count is wrong", "Luka Matic", NOW.atZone(ZONE))));

		mvc.perform(get("/reopen-requests"))
			.andExpect(status().isOk())
			.andExpect(view().name(LogReopenController.QUEUE_VIEW))
			.andExpect(content().string(containsString("Worker count is wrong")));
	}

	@Test
	@WithMockUser
	void history_showsBeforeAndAfterOfEachField() throws Exception {
		RevisionView revision = new RevisionView(12L, NOW.atZone(ZONE), "manager@sitelog.test", "Marko Kovacevic",
				List.of(new EntityChange("DAILY_LOG", LOG_ID, "MODIFIED", null,
						List.of(new FieldChange("history.field.weather", "SUNNY", "RAIN", "weather."))),
						new EntityChange("CREW_ASSIGNMENT", 5L, "MODIFIED", "Kovac Formwork",
								List.of(new FieldChange("history.field.hoursWorked", new BigDecimal("8.00"),
										new BigDecimal("6.50"), null)))));
		when(historyService.history(LOG_ID)).thenReturn(new DailyLogHistory(
				new DailyLogHeader(LOG_ID, 1L, "ZG-01", "Tower A", TODAY.minusDays(1), LogStatus.SIGNED_OFF, 5L), 1,
				List.of(revision), List.of(pending),
				List.of(DomainActionView.of(DomainActionType.LOG_SIGNED_OFF, "Marko Kovacevic", NOW.atZone(ZONE)))));

		mvc.perform(get("/daily-logs/10/history"))
			.andExpect(status().isOk())
			.andExpect(view().name(LogReopenController.HISTORY_VIEW))
			.andExpect(content().string(containsString("Sunny")))
			.andExpect(content().string(containsString("Rain")))
			.andExpect(content().string(containsString("6.5")))
			.andExpect(content().string(org.hamcrest.Matchers.not(containsString("6.50"))))
			.andExpect(content().string(containsString("Kovac Formwork")));
	}

}
