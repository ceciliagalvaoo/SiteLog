package hr.algebra.sitelog.dailylog.web;

import static hr.algebra.sitelog.support.TestEntities.TODAY;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.web.Flash;
import hr.algebra.sitelog.crew.service.CrewOption;
import hr.algebra.sitelog.crew.service.CrewService;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.service.CrewAssignmentService;
import hr.algebra.sitelog.dailylog.service.CrewAssignmentView;
import hr.algebra.sitelog.dailylog.service.DailyLogHeader;
import hr.algebra.sitelog.dailylog.service.DailyLogService;
import hr.algebra.sitelog.support.WebSliceConfig;
import hr.algebra.sitelog.support.WebSliceTest;

@WebMvcTest(CrewAssignmentController.class)
@Import(WebSliceConfig.class)
@WithMockUser
class CrewAssignmentControllerTests extends WebSliceTest {

	@MockitoBean
	CrewAssignmentService crewAssignmentService;

	@MockitoBean
	DailyLogService dailyLogService;

	@MockitoBean
	CrewService crewService;

	@BeforeEach
	void log() {
		when(dailyLogService.getHeaderForChange(10L))
			.thenReturn(new DailyLogHeader(10L, 1L, "ZG-01", "Tower A", TODAY, LogStatus.REOPENED, 3L));
		when(crewService.optionsIncluding(any())).thenReturn(List.of(new CrewOption(1L, "Kovac Formwork", "Formwork", true),
				new CrewOption(2L, "Rebar Team", "Rebar", true)));
	}

	@Test
	void editForm_loadsRowAndItsWorkEntryForTheBreadcrumb() throws Exception {
		when(crewAssignmentService.get(10L, 100L)).thenReturn(new CrewAssignmentView(100L, 1L, "Kovac Formwork",
				"Formwork", true, new BigDecimal("8.00"), 3, "Pour"));
		when(crewAssignmentService.workEntryIdOf(10L, 100L)).thenReturn(20L);

		mvc.perform(get("/daily-logs/10/crew-assignments/100/edit"))
			.andExpect(status().isOk())
			.andExpect(view().name(CrewAssignmentController.FORM_VIEW))
			.andExpect(model().attribute("workEntryId", 20L))
			.andExpect(model().attribute("formAction", "/daily-logs/10/crew-assignments/100"));
	}

	@Test
	void create_withValidRow_redirectsToTheLogWithCreatedFlash() throws Exception {
		when(crewAssignmentService.create(eq(10L), eq(20L), eq(3L), any())).thenReturn(101L);

		mvc.perform(post("/daily-logs/10/work-entries/20/crew-assignments").with(csrf())
			.param("logVersion", "3").param("crewId", "2").param("hoursWorked", "6.5").param("workerCount", "2"))
			.andExpect(redirectedUrl("/daily-logs/10"))
			.andExpect(flash().attribute(Flash.SUCCESS, "crewAssignment.flash.created"));
	}

	@Test
	void create_withZeroWorkers_reRendersWithFieldErrorAndSavesNothing() throws Exception {
		mvc.perform(post("/daily-logs/10/work-entries/20/crew-assignments").with(csrf())
			.param("logVersion", "3").param("crewId", "2").param("hoursWorked", "6").param("workerCount", "0"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("crewAssignmentForm", "workerCount"))
			.andExpect(model().attribute("logVersion", 3L));
		verify(crewAssignmentService, never()).create(anyLong(), anyLong(), any(), any());
	}

	@Test
	void create_whenCrewAlreadyOnTheEntry_showsTheErrorOnCrewId() throws Exception {
		when(crewAssignmentService.create(eq(10L), eq(20L), eq(3L), any()))
			.thenThrow(new InvalidFieldException("crewId", "validation.crewAssignment.crew.duplicate"));

		mvc.perform(post("/daily-logs/10/work-entries/20/crew-assignments").with(csrf())
			.param("logVersion", "3").param("crewId", "1").param("hoursWorked", "6").param("workerCount", "2"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode("crewAssignmentForm", "crewId",
					"validation.crewAssignment.crew.duplicate"))
			.andExpect(content().string(containsString("Rebar Team")));
	}

	@Test
	void update_withStaleLogVersion_renders409() throws Exception {
		doThrow(new ObjectOptimisticLockingFailureException(DailyLog.class, 10L)).when(crewAssignmentService)
			.update(eq(10L), eq(100L), eq(2L), any());

		mvc.perform(post("/daily-logs/10/crew-assignments/100").with(csrf())
			.param("logVersion", "2").param("crewId", "1").param("hoursWorked", "6").param("workerCount", "2"))
			.andExpect(status().isConflict())
			.andExpect(view().name("error/409"));
	}

	@Test
	void delete_redirectsToTheLogWithDeletedFlash() throws Exception {
		mvc.perform(post("/daily-logs/10/crew-assignments/100/delete").with(csrf()).param("logVersion", "3"))
			.andExpect(redirectedUrl("/daily-logs/10"))
			.andExpect(flash().attribute(Flash.SUCCESS, "crewAssignment.flash.deleted"));
		verify(crewAssignmentService).delete(10L, 100L, 3L);
	}

}
