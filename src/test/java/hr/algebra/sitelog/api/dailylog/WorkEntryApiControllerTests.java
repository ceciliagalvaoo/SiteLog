package hr.algebra.sitelog.api.dailylog;

import static hr.algebra.sitelog.api.dailylog.DailyLogApiFixtures.entry;
import static hr.algebra.sitelog.api.dailylog.DailyLogApiFixtures.header;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.LogLockedException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.domain.WorkEntry;
import hr.algebra.sitelog.dailylog.service.DailyLogService;
import hr.algebra.sitelog.dailylog.service.WorkEntryCommand;
import hr.algebra.sitelog.dailylog.service.WorkEntryService;
import hr.algebra.sitelog.support.WebSliceConfig;
import hr.algebra.sitelog.support.WebSliceTest;

@WebMvcTest(WorkEntryApiController.class)
@Import(WebSliceConfig.class)
@WithMockUser(roles = "SITE_MANAGER")
class WorkEntryApiControllerTests extends WebSliceTest {

	static final String BODY = """
			{"logVersion":5,"description":"Slab pour","startTime":"07:00","endTime":"15:30",
			 "crewAssignments":[{"crewId":1,"hoursWorked":7.5,"workerCount":4}]}""";

	@MockitoBean
	WorkEntryService workEntryService;

	@MockitoBean
	DailyLogService dailyLogService;

	@Test
	void list_returnsTheEntriesOfTheLogWithItsVersion() throws Exception {
		when(dailyLogService.getDetail(42L)).thenReturn(log(42L, LogStatus.DRAFT, 5));

		mvc.perform(get("/api/v1/daily-logs/42/work-entries"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(21))
			.andExpect(jsonPath("$[0].logVersion").value(5))
			.andExpect(jsonPath("$[0].totalWorkers").value(4))
			.andExpect(jsonPath("$[0].startTime").value("07:00:00"));
	}

	@Test
	void get_entryOfAnotherLog_is404() throws Exception {
		when(workEntryService.get(42L, 77L)).thenThrow(new NotFoundException(WorkEntry.class, 77L));

		mvc.perform(get("/api/v1/daily-logs/42/work-entries/77")).andExpect(status().isNotFound());
	}

	@Test
	void create_is201WithLocationAndTheIncrementedLogVersion() throws Exception {
		when(workEntryService.create(eq(42L), eq(5L), any())).thenReturn(22L);
		when(workEntryService.get(42L, 22L)).thenReturn(entry(22L));
		when(dailyLogService.getHeader(42L)).thenReturn(header(42L, 6));

		mvc.perform(post("/api/v1/daily-logs/42/work-entries").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(BODY))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "http://localhost/api/v1/daily-logs/42/work-entries/22"))
			.andExpect(jsonPath("$.id").value(22))
			.andExpect(jsonPath("$.logVersion").value(6));

		ArgumentCaptor<WorkEntryCommand> command = ArgumentCaptor.forClass(WorkEntryCommand.class);
		verify(workEntryService).create(eq(42L), eq(5L), command.capture());
		assertThat(command.getValue().description()).isEqualTo("Slab pour");
		assertThat(command.getValue().crewAssignments()).hasSize(1);
	}

	@Test
	void create_withoutLogVersionOrDescription_is400() throws Exception {
		mvc.perform(post("/api/v1/daily-logs/42/work-entries").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"description\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.length()").value(2))
			.andExpect(jsonPath("$.errors[?(@.field == 'description')].code")
				.value("validation.workEntry.description.required"));
		verify(workEntryService, never()).create(anyLong(), any(), any());
	}

	@Test
	void create_inactiveCrew_is422WithTheRowPath() throws Exception {
		when(workEntryService.create(eq(42L), eq(5L), any()))
			.thenThrow(new InvalidFieldException("crewAssignments[0].crewId", "validation.crewAssignment.crew.invalid"));

		mvc.perform(post("/api/v1/daily-logs/42/work-entries").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(BODY))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("crewAssignments[0].crewId"));
	}

	@Test
	void create_onASignedOffLog_is409() throws Exception {
		when(workEntryService.create(eq(42L), eq(5L), any())).thenThrow(new LogLockedException(42L));

		mvc.perform(post("/api/v1/daily-logs/42/work-entries").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("dailyLog.flash.locked"));
	}

	@Test
	void update_is200() throws Exception {
		when(workEntryService.get(42L, 21L)).thenReturn(entry(21L));
		when(dailyLogService.getHeader(42L)).thenReturn(header(42L, 6));

		mvc.perform(put("/api/v1/daily-logs/42/work-entries/21").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(BODY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.logVersion").value(6));

		verify(workEntryService).update(eq(42L), eq(21L), eq(5L), any());
	}

	@Test
	@WithMockUser(roles = "SAFETY_SUPERVISOR")
	void update_byANonManager_is403() throws Exception {
		doThrow(new AuthorizationDeniedException("Access Denied", new AuthorizationDecision(false)))
			.when(workEntryService).update(eq(42L), eq(21L), eq(5L), any());

		mvc.perform(put("/api/v1/daily-logs/42/work-entries/21").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(BODY))
			.andExpect(status().isForbidden());
	}

	@Test
	void delete_is204() throws Exception {
		mvc.perform(delete("/api/v1/daily-logs/42/work-entries/21?logVersion=5").with(csrf()))
			.andExpect(status().isNoContent());

		verify(workEntryService).delete(42L, 21L, 5L);
	}

}
