package hr.algebra.sitelog.api.dailylog;

import static hr.algebra.sitelog.api.dailylog.DailyLogApiFixtures.CREATE_BODY;
import static hr.algebra.sitelog.api.dailylog.DailyLogApiFixtures.log;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.exception.LogLockedException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.domain.Weather;
import hr.algebra.sitelog.dailylog.repository.DailyLogSummary;
import hr.algebra.sitelog.dailylog.service.DailyLogCommand;
import hr.algebra.sitelog.dailylog.service.DailyLogService;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.site.service.SiteService;
import hr.algebra.sitelog.support.WebSliceConfig;
import hr.algebra.sitelog.support.WebSliceTest;

@WebMvcTest(DailyLogApiController.class)
@Import(WebSliceConfig.class)
@WithMockUser(roles = "SITE_MANAGER")
class DailyLogApiControllerTests extends WebSliceTest {

	static final String UPDATE_BODY = """
			{"version":5,"logDate":"2026-11-14","weather":"SUNNY",
			 "workEntries":[{"id":21,"description":"Slab pour","crewAssignments":[{"id":31,"crewId":1,"hoursWorked":8,"workerCount":4}]}]}""";

	@MockitoBean
	DailyLogService dailyLogService;

	@MockitoBean
	SiteService siteService;

	private static AuthorizationDeniedException denied() {
		return new AuthorizationDeniedException("Access Denied", new AuthorizationDecision(false));
	}

	// ---------------------------------------------------------------- collection

	@Test
	void list_returnsThePageOfSummariesNewestFirst() throws Exception {
		when(dailyLogService.listForSite(eq(3L), any())).thenAnswer(call -> new PageImpl<>(
				List.of(new DailyLogSummary(42L, DailyLogApiFixtures.DATE, LogStatus.DRAFT, Weather.RAIN, 0, 2, 3,
						new BigDecimal("21.50"))),
				call.getArgument(1, Pageable.class), 1));

		mvc.perform(get("/api/v1/sites/3/daily-logs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(42))
			.andExpect(jsonPath("$.content[0].workEntryCount").value(2))
			.andExpect(jsonPath("$.content[0].totalHours").value(21.5))
			.andExpect(jsonPath("$.sort[0]").value("logDate,DESC"));

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(dailyLogService).listForSite(eq(3L), pageable.capture());
		assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "logDate"));
	}

	@Test
	void list_unknownSite_is404() throws Exception {
		when(siteService.getSummary(99L)).thenThrow(new NotFoundException(Site.class, 99L));

		mvc.perform(get("/api/v1/sites/99/daily-logs"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		verify(dailyLogService, never()).listForSite(anyLong(), any());
	}

	@Test
	void list_sortByAColumnThatIsNotOffered_is400() throws Exception {
		mvc.perform(get("/api/v1/sites/3/daily-logs?sort=notes"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation.page.invalid"));
	}

	@Test
	void create_is201WithLocationAndTheWholeAggregate() throws Exception {
		when(dailyLogService.create(eq(3L), any())).thenReturn(42L);
		when(dailyLogService.getDetail(42L)).thenReturn(log(42L, LogStatus.DRAFT, 0));

		mvc.perform(post("/api/v1/sites/3/daily-logs").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(CREATE_BODY))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "http://localhost/api/v1/daily-logs/42"))
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.version").value(0))
			.andExpect(jsonPath("$.workEntries[0].crewAssignments[0].crewName").value("Concrete crew A"));

		ArgumentCaptor<DailyLogCommand> command = ArgumentCaptor.forClass(DailyLogCommand.class);
		verify(dailyLogService).create(eq(3L), command.capture());
		assertThat(command.getValue().weather()).isEqualTo(Weather.CLOUDY);
		assertThat(command.getValue().workEntries()).singleElement()
			.satisfies(entry -> assertThat(entry.crewAssignments()).singleElement()
				.satisfies(row -> assertThat(row.hoursWorked()).isEqualByComparingTo("7.5")));
	}

	@Test
	void create_invalidNestedRow_is400WithThePropertyPath() throws Exception {
		mvc.perform(post("/api/v1/sites/3/daily-logs").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
				{"logDate":"2026-11-14","workEntries":[{"description":"Slab","startTime":"10:00","endTime":"09:00",
				  "crewAssignments":[{"crewId":1,"hoursWorked":30,"workerCount":0}]}]}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field == 'workEntries[0].crewAssignments[0].hoursWorked')].code")
				.value("validation.crewAssignment.hoursWorked.range"))
			.andExpect(jsonPath("$.errors[?(@.field == 'workEntries[0].crewAssignments[0].workerCount')].code")
				.value("validation.crewAssignment.workerCount.min"))
			.andExpect(jsonPath("$.errors[?(@.field == 'workEntries[0].endTime')].code")
				.value("validation.workEntry.endTime.afterStart"));
		verify(dailyLogService, never()).create(anyLong(), any());
	}

	@Test
	void create_withoutWorkEntries_is400() throws Exception {
		mvc.perform(post("/api/v1/sites/3/daily-logs").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"logDate\":\"2026-11-14\",\"workEntries\":[]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("workEntries"))
			.andExpect(jsonPath("$.errors[0].code").value("validation.dailyLog.workEntries.required"));
	}

	@Test
	void create_duplicateDate_is422() throws Exception {
		when(dailyLogService.create(eq(3L), any()))
			.thenThrow(new InvalidFieldException("logDate", DailyLogService.DATE_DUPLICATE));

		mvc.perform(post("/api/v1/sites/3/daily-logs").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(CREATE_BODY))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("logDate"))
			.andExpect(jsonPath("$.errors[0].message").value("A daily log already exists for this date"));
	}

	@Test
	@WithMockUser(roles = "INSPECTOR")
	void create_byANonManager_is403() throws Exception {
		when(dailyLogService.create(eq(3L), any())).thenThrow(denied());

		mvc.perform(post("/api/v1/sites/3/daily-logs").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(CREATE_BODY))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	// ---------------------------------------------------------------- single log

	@Test
	void get_returnsTheLogWithItsVersionAndSignOff() throws Exception {
		when(dailyLogService.getDetail(42L)).thenReturn(log(42L, LogStatus.SIGNED_OFF, 6));

		mvc.perform(get("/api/v1/daily-logs/42"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(6))
			.andExpect(jsonPath("$.editable").value(false))
			.andExpect(jsonPath("$.signedOffByName").value("Marko Kovacevic"))
			.andExpect(jsonPath("$.signedOffAt").value("2026-11-14T17:00:00+01:00"))
			.andExpect(jsonPath("$.totalHours").value(7.5))
			.andExpect(jsonPath("$.workEntries[0].logVersion").value(6));
	}

	@Test
	void get_unknownLog_is404() throws Exception {
		when(dailyLogService.getDetail(99L)).thenThrow(new NotFoundException(DailyLog.class, 99L));

		mvc.perform(get("/api/v1/daily-logs/99")).andExpect(status().isNotFound());
	}

	@Test
	void update_passesTheVersionAndReturnsTheNewState() throws Exception {
		when(dailyLogService.getDetail(42L)).thenReturn(log(42L, LogStatus.DRAFT, 6));

		mvc.perform(put("/api/v1/daily-logs/42").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(6));

		ArgumentCaptor<DailyLogCommand> command = ArgumentCaptor.forClass(DailyLogCommand.class);
		verify(dailyLogService).update(eq(42L), eq(5L), command.capture());
		assertThat(command.getValue().workEntries().getFirst().id()).isEqualTo(21L);
		assertThat(command.getValue().workEntries().getFirst().crewAssignments().getFirst().id()).isEqualTo(31L);
	}

	@Test
	void update_withoutVersion_is400() throws Exception {
		mvc.perform(put("/api/v1/daily-logs/42").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(UPDATE_BODY.replace("\"version\":5,", "")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("version"));
		verify(dailyLogService, never()).update(anyLong(), any(), any());
	}

	@Test
	void update_staleVersion_is409() throws Exception {
		doThrow(new ObjectOptimisticLockingFailureException(DailyLog.class, 42L)).when(dailyLogService)
			.update(eq(42L), eq(5L), any());

		mvc.perform(put("/api/v1/daily-logs/42").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("dailyLog.flash.conflict"));
	}

	@Test
	void update_signedOffLog_is409Locked() throws Exception {
		doThrow(new LogLockedException(42L)).when(dailyLogService).update(eq(42L), eq(5L), any());

		mvc.perform(put("/api/v1/daily-logs/42").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("dailyLog.flash.locked"));
	}

	@Test
	void delete_is204AndPassesTheVersion() throws Exception {
		mvc.perform(delete("/api/v1/daily-logs/42?version=5").with(csrf()))
			.andExpect(status().isNoContent());

		verify(dailyLogService).delete(42L, 5L);
	}

	@Test
	void delete_withoutVersion_is400() throws Exception {
		mvc.perform(delete("/api/v1/daily-logs/42").with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		verify(dailyLogService, never()).delete(anyLong(), any());
	}

	@Test
	void delete_notADraft_is409() throws Exception {
		doThrow(new DeletionNotAllowedException(DailyLogService.DELETE_NOT_DRAFT, "signed")).when(dailyLogService)
			.delete(42L, 5L);

		mvc.perform(delete("/api/v1/daily-logs/42?version=5").with(csrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(DailyLogService.DELETE_NOT_DRAFT));
	}

	// ---------------------------------------------------------------- sign-off

	@Test
	void signOff_is200WithTheLockedLog() throws Exception {
		when(dailyLogService.getDetail(42L)).thenReturn(log(42L, LogStatus.SIGNED_OFF, 6));

		mvc.perform(post("/api/v1/daily-logs/42/sign-off").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":5}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SIGNED_OFF"))
			.andExpect(jsonPath("$.editable").value(false));

		verify(dailyLogService).signOff(42L, 5L);
	}

	@Test
	void signOff_alreadySignedOff_is409() throws Exception {
		doThrow(new InvalidTransitionException(DailyLogService.ALREADY_SIGNED_OFF, "signed")).when(dailyLogService)
			.signOff(42L, 5L);

		mvc.perform(post("/api/v1/daily-logs/42/sign-off").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":5}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(DailyLogService.ALREADY_SIGNED_OFF));
	}

	@Test
	void signOff_byTheManagerOfAnotherSite_is403() throws Exception {
		doThrow(denied()).when(dailyLogService).signOff(42L, 5L);

		mvc.perform(post("/api/v1/daily-logs/42/sign-off").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":5}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("error.403.body"));
	}

	@Test
	void signOff_withoutCsrf_is403AndNothingIsSigned() throws Exception {
		mvc.perform(post("/api/v1/daily-logs/42/sign-off").contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":5}"))
			.andExpect(status().isForbidden());
		verify(dailyLogService, never()).signOff(anyLong(), any());
	}

}
