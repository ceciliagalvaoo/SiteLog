package hr.algebra.sitelog.api.dailylog;

import static hr.algebra.sitelog.api.dailylog.DailyLogApiFixtures.entry;
import static hr.algebra.sitelog.api.dailylog.DailyLogApiFixtures.header;
import static hr.algebra.sitelog.api.dailylog.DailyLogApiFixtures.row;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.service.CrewAssignmentCommand;
import hr.algebra.sitelog.dailylog.service.CrewAssignmentService;
import hr.algebra.sitelog.dailylog.service.DailyLogService;
import hr.algebra.sitelog.dailylog.service.WorkEntryService;
import hr.algebra.sitelog.support.WebSliceConfig;
import hr.algebra.sitelog.support.WebSliceTest;

@WebMvcTest(CrewAssignmentApiController.class)
@Import(WebSliceConfig.class)
@WithMockUser(roles = "SITE_MANAGER")
class CrewAssignmentApiControllerTests extends WebSliceTest {

	static final String BASE = "/api/v1/daily-logs/42/work-entries/21/crew-assignments";

	static final String BODY = """
			{"logVersion":5,"crewId":1,"hoursWorked":7.5,"workerCount":4,"task":"Formwork"}""";

	@MockitoBean
	CrewAssignmentService crewAssignmentService;

	@MockitoBean
	WorkEntryService workEntryService;

	@MockitoBean
	DailyLogService dailyLogService;

	@Test
	void list_returnsTheRowsOfTheEntry() throws Exception {
		when(workEntryService.get(42L, 21L)).thenReturn(entry(21L));
		when(dailyLogService.getHeader(42L)).thenReturn(header(42L, 5));

		mvc.perform(get(BASE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(31))
			.andExpect(jsonPath("$[0].crewTrade").value("Concrete"))
			.andExpect(jsonPath("$[0].logVersion").value(5));
	}

	@Test
	void get_rowOfAnotherEntryOfTheSameLog_is404() throws Exception {
		when(crewAssignmentService.workEntryIdOf(42L, 31L)).thenReturn(99L);

		mvc.perform(get(BASE + "/31"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("error.notFound"));
		verify(crewAssignmentService, never()).get(anyLong(), anyLong());
	}

	@Test
	void create_is201WithLocation() throws Exception {
		when(crewAssignmentService.create(eq(42L), eq(21L), eq(5L), any())).thenReturn(32L);
		when(crewAssignmentService.get(42L, 32L)).thenReturn(row(32L));
		when(dailyLogService.getHeader(42L)).thenReturn(header(42L, 6));

		mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "http://localhost" + BASE + "/32"))
			.andExpect(jsonPath("$.id").value(32))
			.andExpect(jsonPath("$.logVersion").value(6));

		ArgumentCaptor<CrewAssignmentCommand> command = ArgumentCaptor.forClass(CrewAssignmentCommand.class);
		verify(crewAssignmentService).create(eq(42L), eq(21L), eq(5L), command.capture());
		assertThat(command.getValue().crewId()).isEqualTo(1L);
		assertThat(command.getValue().workerCount()).isEqualTo(4);
	}

	@Test
	void create_hoursOutOfRange_is400() throws Exception {
		mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(BODY.replace("7.5", "24.5")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("hoursWorked"))
			.andExpect(jsonPath("$.errors[0].message").value("Hours worked must be between 0 and 24"));
	}

	@Test
	void create_crewAlreadyOnTheEntry_is422() throws Exception {
		when(crewAssignmentService.create(eq(42L), eq(21L), eq(5L), any()))
			.thenThrow(new InvalidFieldException("crewId", "validation.crewAssignment.crew.duplicate"));

		mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].code").value("validation.crewAssignment.crew.duplicate"));
	}

	@Test
	void update_staleLogVersion_is409() throws Exception {
		when(crewAssignmentService.workEntryIdOf(42L, 31L)).thenReturn(21L);
		doThrow(new ObjectOptimisticLockingFailureException(DailyLog.class, 42L)).when(crewAssignmentService)
			.update(eq(42L), eq(31L), eq(5L), any());

		mvc.perform(put(BASE + "/31").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("dailyLog.flash.conflict"));
	}

	@Test
	void update_is200() throws Exception {
		when(crewAssignmentService.workEntryIdOf(42L, 31L)).thenReturn(21L);
		when(crewAssignmentService.get(42L, 31L)).thenReturn(row(31L));
		when(dailyLogService.getHeader(42L)).thenReturn(header(42L, 6));

		mvc.perform(put(BASE + "/31").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.task").value("Formwork"));
	}

	@Test
	void delete_is204() throws Exception {
		when(crewAssignmentService.workEntryIdOf(42L, 31L)).thenReturn(21L);

		mvc.perform(delete(BASE + "/31?logVersion=5").with(csrf())).andExpect(status().isNoContent());

		verify(crewAssignmentService).delete(42L, 31L, 5L);
	}

}
