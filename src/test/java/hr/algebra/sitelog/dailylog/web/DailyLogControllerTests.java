package hr.algebra.sitelog.dailylog.web;

import static hr.algebra.sitelog.support.TestEntities.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.exception.LogLockedException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.common.web.Flash;
import hr.algebra.sitelog.crew.service.CrewOption;
import hr.algebra.sitelog.crew.service.CrewService;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.domain.Weather;
import hr.algebra.sitelog.dailylog.service.CrewAssignmentView;
import hr.algebra.sitelog.dailylog.service.DailyLogCommand;
import hr.algebra.sitelog.dailylog.service.DailyLogDetails;
import hr.algebra.sitelog.dailylog.service.DailyLogService;
import hr.algebra.sitelog.dailylog.service.LogReopenService;
import hr.algebra.sitelog.dailylog.service.WorkEntryView;
import hr.algebra.sitelog.site.service.SiteListItem;
import hr.algebra.sitelog.site.service.SiteService;
import hr.algebra.sitelog.support.WebSliceConfig;
import hr.algebra.sitelog.support.WebSliceTest;

/**
 * The nested daily log form (log + work entries + crew rows), sign-off and delete, plus how
 * {@code WebExceptionHandler} turns the service exceptions into 409 / 404 / flash redirects.
 */
@WebMvcTest(DailyLogController.class)
@Import(WebSliceConfig.class)
@WithMockUser
class DailyLogControllerTests extends WebSliceTest {

	private static final Long LOG_ID = 10L;

	private static final Long SITE_ID = 1L;

	@MockitoBean
	DailyLogService dailyLogService;

	@MockitoBean
	SiteService siteService;

	@MockitoBean
	CrewService crewService;

	/** The detail page also shows the reopen panel (AR1); unstubbed it answers null (no panel). */
	@MockitoBean
	LogReopenService logReopenService;

	@BeforeEach
	void site() {
		SiteListItem site = new SiteListItem(SITE_ID, "ZG-01", "Tower A", "Vukovarska 10", LocalDate.of(2026, 1, 1),
				null, "Marko Kovacevic", false);
		when(siteService.getSummary(SITE_ID)).thenReturn(site);
		when(siteService.getSummaryForNewLog(SITE_ID)).thenReturn(site);
		when(crewService.optionsIncluding(any())).thenReturn(List.of(new CrewOption(1L, "Kovac Formwork", "Formwork", true),
				new CrewOption(2L, "Rebar Team", "Rebar", true)));
	}

	private static DailyLogDetails details(LogStatus status, boolean deletable) {
		WorkEntryView entry = new WorkEntryView(20L, "Formwork level 2", "Level 2", LocalTime.of(7, 0),
				LocalTime.of(15, 0), List.of(new CrewAssignmentView(100L, 1L, "Kovac Formwork", "Formwork", true,
						new BigDecimal("8.00"), 3, "Pour slab")));
		return new DailyLogDetails(LOG_ID, SITE_ID, "ZG-01", "Tower A", TODAY.minusDays(1), status, 0, Weather.SUNNY,
				"Dry", null, null, 3L, status.isEditable(), deletable, List.of(entry));
	}

	/**
	 * A complete, valid form (one work entry with one crew row). {@code overrides} are name/value
	 * pairs that REPLACE a default ({@code param()} would add a second value, and binding takes the first).
	 */
	private static MockHttpServletRequestBuilder validForm(MockHttpServletRequestBuilder request, String... overrides) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("version", "3");
		params.put("logDate", TODAY.toString());
		params.put("weather", "RAIN");
		params.put("workEntries[0].description", "Formwork");
		params.put("workEntries[0].startTime", "07:00");
		params.put("workEntries[0].endTime", "15:00");
		params.put("workEntries[0].crewAssignments[0].crewId", "1");
		params.put("workEntries[0].crewAssignments[0].hoursWorked", "8");
		params.put("workEntries[0].crewAssignments[0].workerCount", "3");
		for (int i = 0; i < overrides.length; i += 2) {
			params.put(overrides[i], overrides[i + 1]);
		}
		request.with(csrf());
		params.forEach(request::param);
		return request;
	}

	// ------------------------------------------------------------------ detail / new

	@Test
	void detail_rendersTheLogWithItsWorkEntriesAndCrewRows() throws Exception {
		when(dailyLogService.getDetail(LOG_ID)).thenReturn(details(LogStatus.DRAFT, true));

		mvc.perform(get("/daily-logs/10"))
			.andExpect(status().isOk())
			.andExpect(view().name(DailyLogController.DETAIL_VIEW))
			.andExpect(model().attribute("contextTitle", "Tower A"))
			.andExpect(content().string(containsString("Formwork level 2")))
			.andExpect(content().string(containsString("Kovac Formwork")));
	}

	@Test
	void detail_whenLogMissing_rendersOwn404Page() throws Exception {
		when(dailyLogService.getDetail(99L)).thenThrow(new NotFoundException(DailyLog.class, 99L));

		mvc.perform(get("/daily-logs/99"))
			.andExpect(status().isNotFound())
			.andExpect(view().name("error/404"));
	}

	@Test
	void newForm_startsWithTodayFromTheClockAndOneEmptyEntry() throws Exception {
		mvc.perform(get("/sites/1/daily-logs/new"))
			.andExpect(status().isOk())
			.andExpect(view().name(DailyLogController.FORM_VIEW))
			.andExpect(model().attribute("dailyLogForm", hasProperty("logDate", is(TODAY))))
			.andExpect(model().attribute("dailyLogForm", hasProperty("workEntries", hasSize(1))))
			.andExpect(model().attribute("formAction", "/sites/1/daily-logs"));
	}

	// ------------------------------------------------------------------ create (PRG)

	@Test
	void create_withValidForm_savesAndRedirectsToTheSiteWithSavedFlash() throws Exception {
		when(dailyLogService.create(eq(SITE_ID), any())).thenReturn(LOG_ID);

		mvc.perform(validForm(post("/sites/1/daily-logs")).param("action", "save"))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/sites/1"))
			.andExpect(flash().attribute(Flash.SUCCESS, "dailyLog.flash.saved"))
			.andExpect(flash().attribute(Flash.DETAIL, "dailyLog.flash.saved.detail"))
			.andExpect(flash().attribute(Flash.ARGS,
					org.hamcrest.Matchers.arrayContaining(DailyLogController.longDate(TODAY, Locale.ENGLISH))));

		ArgumentCaptor<DailyLogCommand> command = ArgumentCaptor.forClass(DailyLogCommand.class);
		verify(dailyLogService).create(eq(SITE_ID), command.capture());
		assertThat(command.getValue().logDate()).isEqualTo(TODAY);
		assertThat(command.getValue().workEntries()).singleElement().satisfies(entry -> {
			assertThat(entry.description()).isEqualTo("Formwork");
			assertThat(entry.crewAssignments()).singleElement()
				.satisfies(row -> assertThat(row.hoursWorked()).isEqualByComparingTo("8"));
		});
	}

	@Test
	void create_withHoursOutOfRange_reRendersFormWithErrorOnTheNestedRowAndSavesNothing() throws Exception {
		mvc.perform(validForm(post("/sites/1/daily-logs"),
				"workEntries[0].crewAssignments[0].hoursWorked", "25"))
			.andExpect(status().isOk())
			.andExpect(view().name(DailyLogController.FORM_VIEW))
			.andExpect(model().attributeHasFieldErrors("dailyLogForm", "workEntries[0].crewAssignments[0].hoursWorked"))
			.andExpect(content().string(containsString("Hours worked must be between 0 and 24")));
		verify(dailyLogService, never()).create(anyLong(), any());
	}

	@Test
	void create_withEndTimeBeforeStartTime_reportsTheErrorOnEndTime() throws Exception {
		mvc.perform(validForm(post("/sites/1/daily-logs"), "workEntries[0].endTime", "06:00"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("dailyLogForm", "workEntries[0].endTime"))
			.andExpect(content().string(containsString("End time must be after start time")));
		verify(dailyLogService, never()).create(anyLong(), any());
	}

	@Test
	void create_whenServiceRejectsTheDate_showsTheErrorOnLogDate() throws Exception {
		when(dailyLogService.create(eq(SITE_ID), any()))
			.thenThrow(new InvalidFieldException("logDate", DailyLogService.DATE_FUTURE));

		mvc.perform(validForm(post("/sites/1/daily-logs")))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode("dailyLogForm", "logDate", DailyLogService.DATE_FUTURE))
			.andExpect(content().string(containsString("Date cannot be in the future")));
	}

	@Test
	void create_whenServiceRejectsACrewRow_showsTheErrorOnThatNestedField() throws Exception {
		when(dailyLogService.create(eq(SITE_ID), any())).thenThrow(new InvalidFieldException(
				"workEntries[0].crewAssignments[0].crewId", "validation.crewAssignment.crew.invalid"));

		mvc.perform(validForm(post("/sites/1/daily-logs")))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode("dailyLogForm", "workEntries[0].crewAssignments[0].crewId",
					"validation.crewAssignment.crew.invalid"));
	}

	// ------------------------------------------------------------------ row buttons (no save)

	@Test
	void addEntryButton_addsAWorkEntryWithoutSavingOrShowingErrors() throws Exception {
		mvc.perform(post("/sites/1/daily-logs").with(csrf())
			.param("logDate", TODAY.toString())
			.param("workEntries[0].description", "")
			.param("action", "addEntry"))
			.andExpect(status().isOk())
			.andExpect(view().name(DailyLogController.FORM_VIEW))
			.andExpect(model().attribute("dailyLogForm", hasProperty("workEntries", hasSize(2))))
			.andExpect(model().hasNoErrors());
		verify(dailyLogService, never()).create(anyLong(), any());
	}

	@Test
	void removeEntryButton_removesThatWorkEntryWithoutSaving() throws Exception {
		mvc.perform(validForm(post("/sites/1/daily-logs"))
			.param("workEntries[1].description", "Second")
			.param("action", "removeEntry-0"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("dailyLogForm", hasProperty("workEntries",
					org.hamcrest.Matchers.contains(hasProperty("description", is("Second"))))));
		verify(dailyLogService, never()).create(anyLong(), any());
	}

	@Test
	void addCrewButton_addsACrewRowToThatEntryWithoutSaving() throws Exception {
		mvc.perform(validForm(post("/sites/1/daily-logs")).param("action", "addCrew-0"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("dailyLogForm", hasProperty("workEntries",
					org.hamcrest.Matchers.contains(hasProperty("crewAssignments", hasSize(2))))));
		verify(dailyLogService, never()).create(anyLong(), any());
	}

	@Test
	void removeCrewButtonOnEditForm_doesNotCallUpdate() throws Exception {
		when(dailyLogService.getForEdit(LOG_ID)).thenReturn(details(LogStatus.DRAFT, true));

		mvc.perform(validForm(post("/daily-logs/10")).param("action", "removeCrew-0-0"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("dailyLogForm", hasProperty("workEntries",
					org.hamcrest.Matchers.contains(hasProperty("crewAssignments", hasSize(0))))))
			.andExpect(model().attribute("formAction", "/daily-logs/10"));
		verify(dailyLogService, never()).update(anyLong(), any(), any());
	}

	// ------------------------------------------------------------------ update / conflicts

	@Test
	void update_withValidForm_passesTheFormVersionAndRedirectsToTheLog() throws Exception {
		when(dailyLogService.getForEdit(LOG_ID)).thenReturn(details(LogStatus.DRAFT, true));

		mvc.perform(validForm(post("/daily-logs/10")))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/daily-logs/10"))
			.andExpect(flash().attribute(Flash.SUCCESS, "dailyLog.flash.updated"))
			.andExpect(flash().attribute(Flash.DETAIL, org.hamcrest.Matchers.nullValue()))
			.andExpect(flash().attribute(Flash.ARGS,
					org.hamcrest.Matchers.arrayContaining(DailyLogController.longDate(TODAY, Locale.ENGLISH))));
		verify(dailyLogService).update(eq(LOG_ID), eq(3L), any());
	}

	@Test
	void update_whenVersionIsStale_renders409PageWithReloadLink() throws Exception {
		when(dailyLogService.getForEdit(LOG_ID)).thenReturn(details(LogStatus.DRAFT, true));
		doThrow(new ObjectOptimisticLockingFailureException(DailyLog.class, LOG_ID)).when(dailyLogService)
			.update(eq(LOG_ID), eq(3L), any());

		mvc.perform(validForm(post("/daily-logs/10")))
			.andExpect(status().isConflict())
			.andExpect(view().name("error/409"))
			.andExpect(model().attribute(Flash.ERROR, "dailyLog.flash.conflict"))
			.andExpect(model().attribute("reloadUrl", "/daily-logs/10"))
			.andExpect(content().string(containsString("This daily log was changed by someone else")));
	}

	@Test
	void update_ofSignedOffLog_redirectsToTheLogWithLockedFlash() throws Exception {
		when(dailyLogService.getForEdit(LOG_ID)).thenThrow(new LogLockedException(LOG_ID));

		mvc.perform(validForm(post("/daily-logs/10")))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/daily-logs/10"))
			.andExpect(flash().attribute(Flash.ERROR, "dailyLog.flash.locked"));
		verify(dailyLogService, never()).update(anyLong(), any(), any());
	}

	@Test
	void editForm_ofSignedOffLog_redirectsToTheLogWithLockedFlash() throws Exception {
		when(dailyLogService.getForEdit(LOG_ID)).thenThrow(new LogLockedException(LOG_ID));

		mvc.perform(get("/daily-logs/10/edit"))
			.andExpect(redirectedUrl("/daily-logs/10"))
			.andExpect(flash().attribute(Flash.ERROR, "dailyLog.flash.locked"));
	}

	@Test
	void update_whenTheLockTriggerRejectsTheWrite_renders409WithLockedMessage() throws Exception {
		when(dailyLogService.getForEdit(LOG_ID)).thenReturn(details(LogStatus.DRAFT, true));
		doThrow(new DataIntegrityViolationException("could not execute statement",
				new SQLException("ERROR: daily_log_locked", "P0001"))).when(dailyLogService).update(eq(LOG_ID), eq(3L), any());

		mvc.perform(validForm(post("/daily-logs/10")).header("Referer", "http://localhost/daily-logs/10/edit"))
			.andExpect(status().isConflict())
			.andExpect(view().name("error/409"))
			.andExpect(model().attribute(Flash.ERROR, "dailyLog.flash.locked"))
			.andExpect(model().attribute("reloadUrl", "/daily-logs/10/edit"));
	}

	@Test
	void update_whenTheLockMessageComesWithAnotherSqlState_isNotTreatedAsTheLock() throws Exception {
		when(dailyLogService.getForEdit(LOG_ID)).thenReturn(details(LogStatus.DRAFT, true));
		// e.g. a check constraint whose detail happens to quote the text: SQLSTATE 23514, not P0001
		doThrow(new DataIntegrityViolationException("could not execute statement",
				new SQLException("ERROR: violates check, value 'daily_log_locked'", "23514")))
			.when(dailyLogService).update(eq(LOG_ID), eq(3L), any());

		mvc.perform(validForm(post("/daily-logs/10")).header("Referer", "http://localhost/daily-logs/10/edit"))
			.andExpect(status().isConflict())
			.andExpect(model().attribute(Flash.ERROR, "error.conflict.data"));
	}

	@Test
	void update_whenAConstraintRejectsTheWrite_renders409WithDataConflictMessage() throws Exception {
		when(dailyLogService.getForEdit(LOG_ID)).thenReturn(details(LogStatus.DRAFT, true));
		doThrow(new DataIntegrityViolationException("dup", new SQLException("duplicate key uk_daily_log_site_date")))
			.when(dailyLogService).update(eq(LOG_ID), eq(3L), any());

		mvc.perform(validForm(post("/daily-logs/10")).header("Referer", "https://evil.example/phish"))
			.andExpect(status().isConflict())
			.andExpect(model().attribute(Flash.ERROR, "error.conflict.data"))
			.andExpect(model().attribute("reloadUrl", "/sites"));
	}

	// ------------------------------------------------------------------ sign-off

	@Test
	void signOff_redirectsToTheLogWithSignedOffFlash() throws Exception {
		mvc.perform(post("/daily-logs/10/sign-off").with(csrf()).param("version", "3"))
			.andExpect(redirectedUrl("/daily-logs/10"))
			.andExpect(flash().attribute(Flash.SUCCESS, "dailyLog.flash.signedOff"));
		verify(dailyLogService).signOff(LOG_ID, 3L);
	}

	@Test
	void signOff_ofEmptyLog_goesBackToTheRefererWithErrorFlash() throws Exception {
		doThrow(new InvalidTransitionException(DailyLogService.SIGN_OFF_EMPTY, "empty")).when(dailyLogService)
			.signOff(LOG_ID, 3L);

		mvc.perform(post("/daily-logs/10/sign-off").with(csrf()).param("version", "3")
			.header("Referer", "http://localhost/daily-logs/10"))
			.andExpect(redirectedUrl("/daily-logs/10"))
			.andExpect(flash().attribute(Flash.ERROR, DailyLogService.SIGN_OFF_EMPTY));
	}

	@Test
	void signOff_withStaleVersion_renders409() throws Exception {
		doThrow(new ObjectOptimisticLockingFailureException(DailyLog.class, LOG_ID)).when(dailyLogService)
			.signOff(LOG_ID, 2L);

		mvc.perform(post("/daily-logs/10/sign-off").with(csrf()).param("version", "2"))
			.andExpect(status().isConflict())
			.andExpect(view().name("error/409"));
	}

	@Test
	void signOff_withoutCsrfToken_isForbidden() throws Exception {
		mvc.perform(post("/daily-logs/10/sign-off").param("version", "3"))
			.andExpect(status().isForbidden());
		verify(dailyLogService, never()).signOff(anyLong(), any());
	}

	// ------------------------------------------------------------------ delete

	@Test
	void delete_ofDraft_redirectsToTheSiteLogsTab() throws Exception {
		when(dailyLogService.delete(LOG_ID, 3L)).thenReturn(SITE_ID);

		mvc.perform(post("/daily-logs/10/delete").with(csrf()).param("version", "3"))
			.andExpect(redirectedUrl("/sites/1?tab=logs"))
			.andExpect(flash().attribute(Flash.SUCCESS, "dailyLog.flash.deleted"));
	}

	@Test
	void delete_ofLinkedLog_redirectsToTheLogWithErrorFlash() throws Exception {
		when(dailyLogService.delete(LOG_ID, 3L))
			.thenThrow(new DeletionNotAllowedException(DailyLogService.DELETE_LINKED, "linked"));

		mvc.perform(post("/daily-logs/10/delete").with(csrf()).param("version", "3"))
			.andExpect(redirectedUrl("/daily-logs/10"))
			.andExpect(flash().attribute(Flash.ERROR, DailyLogService.DELETE_LINKED));
	}

	@Test
	void delete_ofSignedOffLog_redirectsWithLockedFlash() throws Exception {
		when(dailyLogService.delete(LOG_ID, 3L)).thenThrow(new LogLockedException(LOG_ID));

		mvc.perform(post("/daily-logs/10/delete").with(csrf()).param("version", "3"))
			.andExpect(redirectedUrl("/daily-logs/10"))
			.andExpect(flash().attribute(Flash.ERROR, "dailyLog.flash.locked"));
	}

}
