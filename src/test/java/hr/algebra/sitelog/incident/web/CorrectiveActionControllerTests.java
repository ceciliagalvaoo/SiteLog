package hr.algebra.sitelog.incident.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.web.Flash;
import hr.algebra.sitelog.incident.domain.CorrectiveActionStatus;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.domain.Severity;
import hr.algebra.sitelog.incident.service.CorrectiveActionCommand;
import hr.algebra.sitelog.incident.service.CorrectiveActionService;
import hr.algebra.sitelog.incident.service.CorrectiveActionView;
import hr.algebra.sitelog.incident.service.IncidentHeader;
import hr.algebra.sitelog.support.WebSliceConfig;
import hr.algebra.sitelog.support.WebSliceTest;
import hr.algebra.sitelog.user.service.UserOption;

@WebMvcTest(CorrectiveActionController.class)
@Import(WebSliceConfig.class)
@WithMockUser(roles = "SAFETY_SUPERVISOR")
class CorrectiveActionControllerTests extends WebSliceTest {

	@MockitoBean
	CorrectiveActionService actionService;

	@BeforeEach
	void incident() {
		when(actionService.incidentForActions(30L)).thenReturn(new IncidentHeader(30L, 1L, "ZG-01", "Tower A",
				"Fall from slab edge", Severity.HIGH, IncidentStatus.SIGNED_OFF, 6L));
		when(actionService.assigneeOptions()).thenReturn(List.of(new UserOption(7L, "Marko Kovacevic", "m@x")));
	}

	@Test
	void newForm_defaultsTheDueDateToOneWeekAndCarriesTheIncidentVersion() throws Exception {
		mvc.perform(get("/incidents/30/corrective-actions/new"))
			.andExpect(status().isOk())
			.andExpect(view().name(CorrectiveActionController.FORM_VIEW))
			.andExpect(model().attribute("formAction", "/incidents/30/corrective-actions"))
			.andExpect(model().attribute(CorrectiveActionController.FORM,
					org.hamcrest.Matchers.hasProperty("incidentVersion", org.hamcrest.Matchers.is(6L))))
			.andExpect(model().attribute(CorrectiveActionController.FORM,
					org.hamcrest.Matchers.hasProperty("dueDate", org.hamcrest.Matchers.is(LocalDate.of(2026, 11, 22)))));
	}

	@Test
	void create_redirectsToTheIncident() throws Exception {
		mvc.perform(post("/incidents/30/corrective-actions").with(csrf()).param("description", "Toe boards")
			.param("assigneeId", "7").param("dueDate", "2026-11-20").param("incidentVersion", "6"))
			.andExpect(redirectedUrl("/incidents/30"))
			.andExpect(flash().attribute(Flash.SUCCESS, "correctiveAction.flash.created"));
		verify(actionService).create(30L, 6L, new CorrectiveActionCommand("Toe boards", 7L, LocalDate.of(2026, 11, 20)));
	}

	@Test
	void create_withoutDescriptionOrDate_reRendersTheForm() throws Exception {
		mvc.perform(post("/incidents/30/corrective-actions").with(csrf()).param("description", "")
			.param("incidentVersion", "6"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors(CorrectiveActionController.FORM, "description", "dueDate"));
		verify(actionService, never()).create(any(), any(), any());
	}

	@Test
	void create_pastDueDateFromTheService_landsOnTheField() throws Exception {
		when(actionService.create(eq(30L), eq(6L), any()))
			.thenThrow(new InvalidFieldException("dueDate", CorrectiveActionService.DUE_DATE_PAST));

		mvc.perform(post("/incidents/30/corrective-actions").with(csrf()).param("description", "Toe boards")
			.param("dueDate", "2026-11-01").param("incidentVersion", "6"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode(CorrectiveActionController.FORM, "dueDate",
					CorrectiveActionService.DUE_DATE_PAST));
	}

	@Test
	void editForm_andUpdate() throws Exception {
		when(actionService.get(30L, 1L)).thenReturn(new CorrectiveActionView(1L, "Toe boards", 7L, "Marko",
				LocalDate.of(2026, 11, 20), CorrectiveActionStatus.OPEN, null, false));

		mvc.perform(get("/incidents/30/corrective-actions/1/edit"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("formAction", "/incidents/30/corrective-actions/1"));
		mvc.perform(post("/incidents/30/corrective-actions/1").with(csrf()).param("description", "Toe boards L3")
			.param("dueDate", "2026-11-21").param("incidentVersion", "6"))
			.andExpect(redirectedUrl("/incidents/30"))
			.andExpect(flash().attribute(Flash.SUCCESS, "correctiveAction.flash.updated"));
		verify(actionService).update(30L, 1L, 6L,
				new CorrectiveActionCommand("Toe boards L3", null, LocalDate.of(2026, 11, 21)));
	}

	@Test
	void update_serviceFieldError_reRendersTheForm() throws Exception {
		doThrow(new InvalidFieldException("assigneeId", CorrectiveActionService.ASSIGNEE_INVALID))
			.when(actionService).update(eq(30L), eq(1L), eq(6L), any());

		mvc.perform(post("/incidents/30/corrective-actions/1").with(csrf()).param("description", "x")
			.param("assigneeId", "11").param("dueDate", "2026-11-21").param("incidentVersion", "6"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode(CorrectiveActionController.FORM, "assigneeId",
					CorrectiveActionService.ASSIGNEE_INVALID));
	}

	@Test
	@WithMockUser(roles = "SITE_MANAGER")
	void markDone_byTheAssignee_redirectsWithAFlash() throws Exception {
		mvc.perform(post("/incidents/30/corrective-actions/1/done").with(csrf()).param("version", "6"))
			.andExpect(redirectedUrl("/incidents/30"))
			.andExpect(flash().attribute(Flash.SUCCESS, "correctiveAction.flash.done"));
		verify(actionService).markDone(30L, 1L, 6L);
	}

	@Test
	@WithMockUser(roles = "INSPECTOR")
	void markDone_bySomeoneElse_is403() throws Exception {
		doThrow(new AccessDeniedException("no")).when(actionService).markDone(30L, 1L, 6L);

		mvc.perform(post("/incidents/30/corrective-actions/1/done").with(csrf()).param("version", "6"))
			.andExpect(status().isForbidden());
	}

	@Test
	void delete_redirectsWithAFlash() throws Exception {
		mvc.perform(post("/incidents/30/corrective-actions/1/delete").with(csrf()).param("version", "6"))
			.andExpect(redirectedUrl("/incidents/30"))
			.andExpect(flash().attribute(Flash.SUCCESS, "correctiveAction.flash.deleted"));
		verify(actionService).delete(30L, 1L, 6L);
	}

}
