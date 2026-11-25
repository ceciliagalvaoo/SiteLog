package hr.algebra.sitelog.api.reopen;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.domain.ReopenStatus;
import hr.algebra.sitelog.dailylog.service.DailyLogHistoryService;
import hr.algebra.sitelog.dailylog.service.DailyLogService;
import hr.algebra.sitelog.dailylog.service.EntityChange;
import hr.algebra.sitelog.dailylog.service.FieldChange;
import hr.algebra.sitelog.dailylog.service.LogReopenService;
import hr.algebra.sitelog.dailylog.service.PendingReopenItem;
import hr.algebra.sitelog.dailylog.service.ReopenPanel;
import hr.algebra.sitelog.dailylog.service.ReopenRequestView;
import hr.algebra.sitelog.dailylog.service.RevisionView;
import hr.algebra.sitelog.support.WebSliceConfig;
import hr.algebra.sitelog.support.WebSliceTest;

@WebMvcTest(ReopenApiController.class)
@Import(WebSliceConfig.class)
@WithMockUser(roles = "SITE_MANAGER")
class ReopenApiControllerTests extends WebSliceTest {

	static final ZonedDateTime AT = ZonedDateTime.of(2026, 11, 20, 18, 0, 0, 0, ZoneId.of("Europe/Zagreb"));

	@MockitoBean
	LogReopenService reopenService;

	@MockitoBean
	DailyLogService dailyLogService;

	@MockitoBean
	DailyLogHistoryService historyService;

	private static ReopenRequestView pending(Long id) {
		return new ReopenRequestView(id, ReopenStatus.PENDING, "Hours entered twice", "Marko Kovacevic", AT, null, null,
				null);
	}

	private static ReopenRequestView decided(Long id, ReopenStatus status, String note) {
		return new ReopenRequestView(id, status, "Hours entered twice", "Marko Kovacevic", AT, "Sanja Babic",
				AT.plusHours(1), note);
	}

	@Test
	void list_returnsTheHistoryNewestFirst() throws Exception {
		when(reopenService.panel(42L)).thenReturn(new ReopenPanel(42L, LogStatus.SIGNED_OFF, pending(8L),
				List.of(pending(8L), decided(7L, ReopenStatus.REJECTED, "Hours match")), false));

		mvc.perform(get("/api/v1/daily-logs/42/reopen-requests"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(8))
			.andExpect(jsonPath("$[0].status").value("PENDING"))
			.andExpect(jsonPath("$[1].decisionNote").value("Hours match"))
			.andExpect(jsonPath("$[1].decidedAt").value("2026-11-20T19:00:00+01:00"));
	}

	@Test
	void list_unknownLog_is404() throws Exception {
		when(reopenService.panel(99L)).thenThrow(new NotFoundException(DailyLog.class, 99L));

		mvc.perform(get("/api/v1/daily-logs/99/reopen-requests")).andExpect(status().isNotFound());
	}

	@Test
	void request_is201WithLocation() throws Exception {
		when(reopenService.request(42L, 4L, "Hours entered twice")).thenReturn(8L);
		when(reopenService.history(42L)).thenReturn(List.of(pending(8L)));

		mvc.perform(post("/api/v1/daily-logs/42/reopen-requests").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":4,\"reason\":\"Hours entered twice\"}"))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "http://localhost/api/v1/daily-logs/42/reopen-requests/8"))
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.dailyLogId").value(42));
	}

	@Test
	void request_withoutReason_is400AndTheServiceIsNotCalled() throws Exception {
		mvc.perform(post("/api/v1/daily-logs/42/reopen-requests").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":4,\"reason\":\"  \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("validation.dailyLog.reopenReason.required"));
		verify(reopenService, never()).request(anyLong(), any(), any());
	}

	@Test
	void request_alreadyPending_is409() throws Exception {
		when(reopenService.request(42L, 4L, "Again"))
			.thenThrow(new InvalidTransitionException(LogReopenService.ALREADY_PENDING, "pending"));

		mvc.perform(post("/api/v1/daily-logs/42/reopen-requests").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":4,\"reason\":\"Again\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(LogReopenService.ALREADY_PENDING));
	}

	@Test
	void approve_byASiteManager_is403() throws Exception {
		doThrow(new AuthorizationDeniedException("Access Denied", new AuthorizationDecision(false)))
			.when(reopenService).approve(eq(42L), eq(8L), any());

		mvc.perform(post("/api/v1/daily-logs/42/reopen-requests/8/approve").with(csrf()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("error.403.body"));
	}

	@Test
	@WithMockUser(roles = "SAFETY_SUPERVISOR")
	void approve_withoutBody_is200WithTheDecision() throws Exception {
		when(reopenService.history(42L)).thenReturn(List.of(decided(8L, ReopenStatus.APPROVED, null)));

		mvc.perform(post("/api/v1/daily-logs/42/reopen-requests/8/approve").with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.decidedByName").value("Sanja Babic"));
		verify(reopenService).approve(eq(42L), eq(8L), isNull());
	}

	@Test
	@WithMockUser(roles = "SAFETY_SUPERVISOR")
	void approve_alreadyDecided_is409() throws Exception {
		doThrow(new InvalidTransitionException(LogReopenService.ALREADY_DECIDED, "decided")).when(reopenService)
			.approve(42L, 8L, "ok");

		mvc.perform(post("/api/v1/daily-logs/42/reopen-requests/8/approve").with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"ok\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(LogReopenService.ALREADY_DECIDED));
	}

	@Test
	@WithMockUser(roles = "SAFETY_SUPERVISOR")
	void reject_is200_andWithoutReason_is400() throws Exception {
		when(reopenService.history(42L)).thenReturn(List.of(decided(8L, ReopenStatus.REJECTED, "Hours match")));

		mvc.perform(post("/api/v1/daily-logs/42/reopen-requests/8/reject").with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Hours match\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.decisionNote").value("Hours match"));
		verify(reopenService).reject(42L, 8L, "Hours match");

		mvc.perform(post("/api/v1/daily-logs/42/reopen-requests/8/reject").with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].code").value("validation.reopen.rejectReason.required"));
	}

	@Test
	@WithMockUser(roles = "SAFETY_SUPERVISOR")
	void reject_unknownRequest_is404() throws Exception {
		doThrow(new NotFoundException(DailyLog.class, 99L)).when(reopenService).reject(42L, 99L, "No");

		mvc.perform(post("/api/v1/daily-logs/42/reopen-requests/99/reject").with(csrf())
			.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"No\"}"))
			.andExpect(status().isNotFound());
	}

	@Test
	void request_serviceRuleOnTheReason_is422() throws Exception {
		when(reopenService.request(42L, 4L, "x"))
			.thenThrow(new InvalidFieldException("reason", LogReopenService.REASON_REQUIRED));

		mvc.perform(post("/api/v1/daily-logs/42/reopen-requests").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":4,\"reason\":\"x\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("reason"));
	}

	@Test
	@WithMockUser(roles = "SAFETY_SUPERVISOR")
	void pending_returnsTheQueue() throws Exception {
		when(reopenService.pendingRequests()).thenReturn(List.of(new PendingReopenItem(8L, 42L, "ZG-01", "Tower A",
				LocalDate.of(2026, 11, 20), "Hours entered twice", "Marko Kovacevic", AT)));

		mvc.perform(get("/api/v1/reopen-requests"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(8))
			.andExpect(jsonPath("$[0].dailyLogId").value(42))
			.andExpect(jsonPath("$[0].logDate").value("2026-11-20"));
	}

	@Test
	@WithMockUser(roles = "INSPECTOR")
	void pending_forAnyoneButASupervisor_is403Problem() throws Exception {
		when(reopenService.pendingRequests())
			.thenThrow(new AuthorizationDeniedException("Access Denied", new AuthorizationDecision(false)));

		mvc.perform(get("/api/v1/reopen-requests")).andExpect(status().isForbidden());
	}

	@Test
	void revisions_returnsBeforeAndAfter_andAnUnknownLogIs404() throws Exception {
		when(historyService.revisions(42L)).thenReturn(List.of(new RevisionView(17L, AT, "manager@sitelog.test",
				"Marko Kovacevic", List.of(new EntityChange("DAILY_LOG", 42L, "MODIFIED", null,
						List.of(new FieldChange("dailyLog.field.weather", "SUNNY", "RAIN", "weather.")))))));

		mvc.perform(get("/api/v1/daily-logs/42/revisions"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].revision").value(17))
			.andExpect(jsonPath("$[0].changes[0].changeType").value("MODIFIED"))
			.andExpect(jsonPath("$[0].changes[0].fields[0].before").value("SUNNY"))
			.andExpect(jsonPath("$[0].changes[0].fields[0].after").value("RAIN"));

		when(dailyLogService.getHeader(99L)).thenThrow(new NotFoundException(DailyLog.class, 99L));
		mvc.perform(get("/api/v1/daily-logs/99/revisions")).andExpect(status().isNotFound());
		verify(historyService, never()).revisions(99L);
	}

}
