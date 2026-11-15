package hr.algebra.sitelog.dailylog.web;

import static hr.algebra.sitelog.support.TestEntities.TODAY;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import hr.algebra.sitelog.common.exception.LogLockedException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.common.web.Flash;
import hr.algebra.sitelog.crew.service.CrewOption;
import hr.algebra.sitelog.crew.service.CrewService;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.domain.WorkEntry;
import hr.algebra.sitelog.dailylog.service.DailyLogHeader;
import hr.algebra.sitelog.dailylog.service.DailyLogService;
import hr.algebra.sitelog.dailylog.service.WorkEntryService;
import hr.algebra.sitelog.support.WebSliceConfig;
import hr.algebra.sitelog.support.WebSliceTest;

@WebMvcTest(WorkEntryController.class)
@Import(WebSliceConfig.class)
@WithMockUser
class WorkEntryControllerTests extends WebSliceTest {

	@MockitoBean
	WorkEntryService workEntryService;

	@MockitoBean
	DailyLogService dailyLogService;

	@MockitoBean
	CrewService crewService;

	@BeforeEach
	void log() {
		when(dailyLogService.getHeaderForChange(10L))
			.thenReturn(new DailyLogHeader(10L, 1L, "ZG-01", "Tower A", TODAY, LogStatus.DRAFT, 3L));
		when(crewService.optionsIncluding(any())).thenReturn(List.of(new CrewOption(1L, "Kovac Formwork", "Formwork", true)));
	}

	/** A valid entry; {@code overrides} are name/value pairs that replace a default. */
	private static MockHttpServletRequestBuilder validEntry(MockHttpServletRequestBuilder request, String... overrides) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("logVersion", "3");
		params.put("description", "Excavation");
		params.put("startTime", "07:00");
		params.put("endTime", "12:00");
		params.put("crewAssignments[0].crewId", "1");
		params.put("crewAssignments[0].hoursWorked", "5");
		params.put("crewAssignments[0].workerCount", "2");
		for (int i = 0; i < overrides.length; i += 2) {
			params.put(overrides[i], overrides[i + 1]);
		}
		request.with(csrf());
		params.forEach(request::param);
		return request;
	}

	@Test
	void newForm_carriesTheLogVersionShownOnThePage() throws Exception {
		mvc.perform(get("/daily-logs/10/work-entries/new"))
			.andExpect(status().isOk())
			.andExpect(view().name(WorkEntryController.FORM_VIEW))
			.andExpect(model().attribute("logVersion", 3L))
			.andExpect(model().attribute("formAction", "/daily-logs/10/work-entries"));
	}

	@Test
	void newForm_onSignedOffLog_redirectsWithLockedFlash() throws Exception {
		when(dailyLogService.getHeaderForChange(10L)).thenThrow(new LogLockedException(10L));

		mvc.perform(get("/daily-logs/10/work-entries/new"))
			.andExpect(redirectedUrl("/daily-logs/10"))
			.andExpect(flash().attribute(Flash.ERROR, "dailyLog.flash.locked"));
	}

	@Test
	void create_withValidForm_savesWithTheFormLogVersionAndRedirectsToTheLog() throws Exception {
		when(workEntryService.create(eq(10L), eq(3L), any())).thenReturn(30L);

		mvc.perform(validEntry(post("/daily-logs/10/work-entries")))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/daily-logs/10"))
			.andExpect(flash().attribute(Flash.SUCCESS, "workEntry.flash.created"));
		verify(workEntryService).create(eq(10L), eq(3L), any());
	}

	@Test
	void create_withEndTimeBeforeStart_reRendersAndKeepsTheSubmittedLogVersion() throws Exception {
		mvc.perform(validEntry(post("/daily-logs/10/work-entries"), "logVersion", "2", "endTime", "06:00"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("workEntryForm", "endTime"))
			.andExpect(model().attribute("logVersion", 2L))
			.andExpect(content().string(containsString("End time must be after start time")));
		verify(workEntryService, never()).create(anyLong(), any(), any());
	}

	@Test
	void addCrewButton_addsARowWithoutSaving() throws Exception {
		mvc.perform(validEntry(post("/daily-logs/10/work-entries")).param("action", "addCrew"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("workEntryForm", hasProperty("crewAssignments", hasSize(2))))
			.andExpect(model().hasNoErrors());
		verify(workEntryService, never()).create(anyLong(), any(), any());
	}

	@Test
	void update_withStaleLogVersion_renders409() throws Exception {
		doThrow(new ObjectOptimisticLockingFailureException(DailyLog.class, 10L)).when(workEntryService)
			.update(eq(10L), eq(20L), eq(2L), any());

		mvc.perform(validEntry(post("/daily-logs/10/work-entries/20"), "logVersion", "2"))
			.andExpect(status().isConflict())
			.andExpect(view().name("error/409"))
			.andExpect(model().attribute("reloadUrl", "/daily-logs/10"));
	}

	@Test
	void editForm_ofEntryOfAnotherLog_renders404() throws Exception {
		when(workEntryService.get(10L, 77L)).thenThrow(new NotFoundException(WorkEntry.class, 77L));

		mvc.perform(get("/daily-logs/10/work-entries/77/edit"))
			.andExpect(status().isNotFound())
			.andExpect(view().name("error/404"));
	}

	@Test
	void delete_redirectsToTheLogWithDeletedFlash() throws Exception {
		mvc.perform(post("/daily-logs/10/work-entries/20/delete").with(csrf()).param("logVersion", "3"))
			.andExpect(redirectedUrl("/daily-logs/10"))
			.andExpect(flash().attribute(Flash.SUCCESS, "workEntry.flash.deleted"));
		verify(workEntryService).delete(10L, 20L, 3L);
	}

	@Test
	void delete_ofEntryOfSignedOffLog_redirectsWithLockedFlash() throws Exception {
		doThrow(new LogLockedException(10L)).when(workEntryService).delete(10L, 20L, 3L);

		mvc.perform(post("/daily-logs/10/work-entries/20/delete").with(csrf()).param("logVersion", "3"))
			.andExpect(redirectedUrl("/daily-logs/10"))
			.andExpect(flash().attribute(Flash.ERROR, "dailyLog.flash.locked"));
	}

}
