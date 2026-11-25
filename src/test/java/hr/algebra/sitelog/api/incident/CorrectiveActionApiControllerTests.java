package hr.algebra.sitelog.api.incident;

import static hr.algebra.sitelog.api.incident.IncidentApiFixtures.ACTION_BODY;
import static hr.algebra.sitelog.api.incident.IncidentApiFixtures.incident;
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

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.incident.domain.Incident;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.domain.Severity;
import hr.algebra.sitelog.incident.service.CorrectiveActionCommand;
import hr.algebra.sitelog.incident.service.CorrectiveActionService;
import hr.algebra.sitelog.incident.service.IncidentService;
import hr.algebra.sitelog.support.WebSliceConfig;
import hr.algebra.sitelog.support.WebSliceTest;

@WebMvcTest(CorrectiveActionApiController.class)
@Import(WebSliceConfig.class)
@WithMockUser(roles = "SAFETY_SUPERVISOR")
class CorrectiveActionApiControllerTests extends WebSliceTest {

	static final String BASE = "/api/v1/incidents/12/corrective-actions";

	@MockitoBean
	CorrectiveActionService actionService;

	@MockitoBean
	IncidentService incidentService;

	private void incidentAtVersion(long version) {
		when(incidentService.getDetail(12L))
			.thenReturn(incident(12L, Severity.HIGH, IncidentStatus.SIGNED_OFF, version, List.of()));
	}

	@Test
	void list_returnsTheActionsWithTheIncidentVersion() throws Exception {
		incidentAtVersion(3);

		mvc.perform(get(BASE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(5))
			.andExpect(jsonPath("$[0].status").value("OPEN"))
			.andExpect(jsonPath("$[0].incidentVersion").value(3));
	}

	@Test
	void get_unknownActionOrIncident_is404() throws Exception {
		incidentAtVersion(3);
		mvc.perform(get(BASE + "/99")).andExpect(status().isNotFound());

		when(incidentService.getDetail(98L)).thenThrow(new NotFoundException(Incident.class, 98L));
		mvc.perform(get("/api/v1/incidents/98/corrective-actions")).andExpect(status().isNotFound());
	}

	@Test
	void create_is201WithLocationAndTheNewIncidentVersion() throws Exception {
		when(actionService.create(eq(12L), eq(3L), any())).thenReturn(5L);
		incidentAtVersion(4);

		mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ACTION_BODY))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "http://localhost" + BASE + "/5"))
			.andExpect(jsonPath("$.incidentVersion").value(4))
			.andExpect(jsonPath("$.assigneeName").value("Marko Kovacevic"));

		ArgumentCaptor<CorrectiveActionCommand> command = ArgumentCaptor.forClass(CorrectiveActionCommand.class);
		verify(actionService).create(eq(12L), eq(3L), command.capture());
		assertThat(command.getValue().dueDate()).isEqualTo(LocalDate.of(2026, 12, 1));
		assertThat(command.getValue().assigneeId()).isEqualTo(7L);
	}

	@Test
	void create_withoutDescriptionAndVersion_is400() throws Exception {
		mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"dueDate\":\"2026-12-01\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field == 'description')].code")
				.value("validation.correctiveAction.action.required"))
			.andExpect(jsonPath("$.errors[?(@.field == 'incidentVersion')]").exists());
		verify(actionService, never()).create(anyLong(), any(), any());
	}

	@Test
	void create_pastDueDate_is422() throws Exception {
		when(actionService.create(eq(12L), eq(3L), any()))
			.thenThrow(new InvalidFieldException("dueDate", CorrectiveActionService.DUE_DATE_PAST));

		mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ACTION_BODY))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("dueDate"));
	}

	@Test
	@WithMockUser(roles = "SITE_MANAGER")
	void create_byASiteManager_is403() throws Exception {
		when(actionService.create(eq(12L), eq(3L), any()))
			.thenThrow(new AuthorizationDeniedException("Access Denied", new AuthorizationDecision(false)));

		mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ACTION_BODY))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("error.403.body"));
	}

	@Test
	void update_staleIncidentVersion_is409WithTheIncidentMessage() throws Exception {
		doThrow(new ObjectOptimisticLockingFailureException(Incident.class, 12L)).when(actionService)
			.update(eq(12L), eq(5L), eq(3L), any());

		mvc.perform(put(BASE + "/5").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ACTION_BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("incident.flash.conflict"));
	}

	@Test
	void update_is200() throws Exception {
		incidentAtVersion(4);

		mvc.perform(put(BASE + "/5").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ACTION_BODY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(5))
			.andExpect(jsonPath("$.incidentVersion").value(4));
		verify(actionService).update(eq(12L), eq(5L), eq(3L), any());
	}

	@Test
	void delete_is204_andWithoutVersion_is400() throws Exception {
		mvc.perform(delete(BASE + "/5?incidentVersion=3").with(csrf())).andExpect(status().isNoContent());
		verify(actionService).delete(12L, 5L, 3L);

		mvc.perform(delete(BASE + "/5").with(csrf())).andExpect(status().isBadRequest());
	}

	@Test
	void markDone_is200_andADoneActionIs409() throws Exception {
		incidentAtVersion(4);
		mvc.perform(post(BASE + "/5/done").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"incidentVersion\":3}"))
			.andExpect(status().isOk());
		verify(actionService).markDone(12L, 5L, 3L);

		doThrow(new InvalidTransitionException(CorrectiveActionService.ACTION_DONE, "done")).when(actionService)
			.markDone(12L, 5L, 4L);
		mvc.perform(post(BASE + "/5/done").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"incidentVersion\":4}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(CorrectiveActionService.ACTION_DONE));
	}

}
