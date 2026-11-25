package hr.algebra.sitelog.incident.web;

import static hr.algebra.sitelog.support.TestEntities.NOW;
import static hr.algebra.sitelog.support.TestEntities.ZONE;
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

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import hr.algebra.sitelog.audit.domain.DomainActionType;
import hr.algebra.sitelog.audit.service.DomainActionView;
import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.exception.MissingSeverityFieldsException;
import hr.algebra.sitelog.common.web.Flash;
import hr.algebra.sitelog.crew.service.CrewOption;
import hr.algebra.sitelog.crew.service.CrewService;
import hr.algebra.sitelog.incident.domain.CorrectiveActionStatus;
import hr.algebra.sitelog.incident.domain.Incident;
import hr.algebra.sitelog.incident.domain.IncidentCategory;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.domain.Severity;
import hr.algebra.sitelog.incident.service.CorrectiveActionView;
import hr.algebra.sitelog.incident.service.IncidentAccess;
import hr.algebra.sitelog.incident.service.IncidentCommand;
import hr.algebra.sitelog.incident.service.IncidentDetails;
import hr.algebra.sitelog.incident.service.IncidentFilter;
import hr.algebra.sitelog.incident.service.IncidentListItem;
import hr.algebra.sitelog.incident.service.IncidentService;
import hr.algebra.sitelog.site.service.SiteListItem;
import hr.algebra.sitelog.site.service.SiteService;
import hr.algebra.sitelog.support.WebSliceConfig;
import hr.algebra.sitelog.support.WebSliceTest;

/** Incident screens: list, report / edit form with the AR2 field rules, detail, workflow POSTs (PRG, 403, 409). */
@WebMvcTest(IncidentController.class)
@Import(WebSliceConfig.class)
@WithMockUser
class IncidentControllerTests extends WebSliceTest {

	private static final Long ID = 30L;

	private static final Long SITE_ID = 1L;

	@MockitoBean
	IncidentService incidentService;

	@MockitoBean
	SiteService siteService;

	@MockitoBean
	CrewService crewService;

	/** The detail template asks {@code @incidentAccess} for non-supervisors (not part of the slice). */
	@MockitoBean(name = "incidentAccess")
	IncidentAccess incidentAccess;

	@BeforeEach
	void site() {
		when(siteService.getSummary(SITE_ID)).thenReturn(new SiteListItem(SITE_ID, "ZG-01", "Tower A", "Ilica 1",
				LocalDate.of(2026, 1, 1), null, "Marko Kovacevic", false));
		when(crewService.optionsIncluding(any())).thenReturn(List.of(new CrewOption(3L, "Kovac Formwork", "Formwork", true)));
	}

	private static IncidentDetails details(Severity severity, IncidentStatus status, List<String> missing) {
		return new IncidentDetails(ID, SITE_ID, "ZG-01", "Tower A", 12L, LocalDate.of(2026, 11, 14),
				NOW.atZone(ZONE), "Fall from slab edge", "A worker fell", severity, IncidentCategory.FALL_FROM_HEIGHT,
				status, "Edge open", "Ivan", "First aid", true, "Marko Kovacevic", null, null, 4L,
				List.of(new CrewOption(3L, "Kovac Formwork", "Formwork", true)),
				List.of(new CorrectiveActionView(1L, "Reinstall edge protection", 7L, "Marko Kovacevic",
						LocalDate.of(2026, 11, 20), CorrectiveActionStatus.OPEN, null, false)),
				missing, List.of(DomainActionView.of(DomainActionType.INCIDENT_REPORTED, "Marko Kovacevic",
						NOW.atZone(ZONE))));
	}

	private static MockHttpServletRequestBuilder lowForm(MockHttpServletRequestBuilder request) {
		return form(request, "LOW", "Slip on stairs");
	}

	private static MockHttpServletRequestBuilder form(MockHttpServletRequestBuilder request, String severity,
			String title) {
		return request.with(csrf()).param("title", title).param("description", "Wet stairs")
			.param("occurredAt", "2026-11-15T08:30").param("severity", severity).param("category", "SLIP_TRIP")
			.param("affectedCrewIds", "3");
	}

	// ------------------------------------------------------------------ lists and detail

	@Test
	void list_rendersAllIncidentsWithTheFilters() throws Exception {
		when(incidentService.list(any(), any())).thenReturn(new PageImpl<>(List.of(new IncidentListItem(ID, SITE_ID,
				"ZG-01", "Tower A", NOW.atZone(ZONE), "Fall from slab edge", Severity.HIGH,
				IncidentCategory.FALL_FROM_HEIGHT, IncidentStatus.REPORTED, "Marko Kovacevic"))));

		mvc.perform(get("/incidents").param("status", "REPORTED").param("severity", "HIGH"))
			.andExpect(status().isOk())
			.andExpect(view().name(IncidentController.LIST_VIEW))
			.andExpect(model().attribute("listUrl", "/incidents"))
			.andExpect(content().string(containsString("Fall from slab edge")));

		ArgumentCaptor<IncidentFilter> filter = ArgumentCaptor.forClass(IncidentFilter.class);
		verify(incidentService).list(filter.capture(), any(Pageable.class));
		org.assertj.core.api.Assertions.assertThat(filter.getValue())
			.isEqualTo(new IncidentFilter(null, IncidentStatus.REPORTED, Severity.HIGH));
	}

	@Test
	void siteList_filtersBySiteAndShowsTheSite() throws Exception {
		when(incidentService.list(any(), any())).thenReturn(new PageImpl<>(List.of()));

		mvc.perform(get("/sites/{id}/incidents", SITE_ID))
			.andExpect(status().isOk())
			.andExpect(model().attribute("listUrl", "/sites/1/incidents"))
			.andExpect(content().string(containsString("No incidents")));
		verify(incidentService).list(eq(IncidentFilter.forSite(SITE_ID)), any());
	}

	@Test
	@WithMockUser(roles = "SAFETY_SUPERVISOR")
	void detail_forTheSupervisor_showsTheWorkflowAndTheTimeline() throws Exception {
		when(incidentService.getDetail(ID)).thenReturn(details(Severity.HIGH, IncidentStatus.UNDER_REVIEW, List.of()));

		mvc.perform(get("/incidents/{id}", ID))
			.andExpect(status().isOk())
			.andExpect(view().name(IncidentController.DETAIL_VIEW))
			.andExpect(content().string(containsString("/incidents/30/sign-off")))
			.andExpect(content().string(containsString("/incidents/30/corrective-actions/new")))
			.andExpect(content().string(containsString("Timeline")));
	}

	@Test
	void detail_forAReader_showsNoWorkflowButton() throws Exception {
		when(incidentService.getDetail(ID))
			.thenReturn(details(Severity.HIGH, IncidentStatus.UNDER_REVIEW, List.of("rootCause")));

		mvc.perform(get("/incidents/{id}", ID))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.not(containsString("/incidents/30/sign-off"))))
			.andExpect(content().string(containsString("Root cause")));
	}

	// ------------------------------------------------------------------ report

	@Test
	void newForm_rendersTheEmptyForm() throws Exception {
		when(incidentService.dailyLogOptionsForReport(SITE_ID)).thenReturn(List.of());

		mvc.perform(get("/sites/{id}/incidents/new", SITE_ID))
			.andExpect(status().isOk())
			.andExpect(view().name(IncidentController.FORM_VIEW))
			.andExpect(model().attribute("formAction", "/sites/1/incidents"));
	}

	@Test
	void newForm_forSomeoneWhoMayNotReportOnTheSite_is403() throws Exception {
		when(incidentService.dailyLogOptionsForReport(SITE_ID)).thenThrow(new AccessDeniedException("not your site"));

		mvc.perform(get("/sites/{id}/incidents/new", SITE_ID)).andExpect(status().isForbidden());
	}

	@Test
	void create_validLowIncident_redirectsToTheDetailWithAFlash() throws Exception {
		when(incidentService.report(eq(SITE_ID), any())).thenReturn(ID);

		mvc.perform(lowForm(post("/sites/{id}/incidents", SITE_ID)))
			.andExpect(redirectedUrl("/incidents/30"))
			.andExpect(flash().attribute(Flash.SUCCESS, "incident.flash.reported"));

		ArgumentCaptor<IncidentCommand> command = ArgumentCaptor.forClass(IncidentCommand.class);
		verify(incidentService).report(eq(SITE_ID), command.capture());
		org.assertj.core.api.Assertions.assertThat(command.getValue().occurredAt())
			.as("datetime-local is read in the sites' zone").isEqualTo(java.time.Instant.parse("2026-11-15T07:30:00Z"));
		org.assertj.core.api.Assertions.assertThat(command.getValue().affectedCrewIds()).containsExactly(3L);
	}

	@Test
	void create_highIncidentWithoutTheExtraFields_reRendersWithAnErrorOnEachAndReportsNothing() throws Exception {
		mvc.perform(form(post("/sites/{id}/incidents", SITE_ID), "HIGH", "Fall"))
			.andExpect(status().isOk())
			.andExpect(view().name(IncidentController.FORM_VIEW))
			.andExpect(model().attributeHasFieldErrors(IncidentController.FORM, "rootCause", "witnesses",
					"medicalTreatment", "authorityNotified"))
			.andExpect(content().string(containsString("Root cause is required")));
		verify(incidentService, never()).report(anyLong(), any());
	}

	@Test
	void create_lowIncidentWithoutTitle_hasNoSeverityFieldErrors() throws Exception {
		mvc.perform(form(post("/sites/{id}/incidents", SITE_ID), "LOW", ""))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors(IncidentController.FORM, "title"))
			.andExpect(model().attributeHasNoErrors("site"))
			.andExpect(model().errorCount(1));
	}

	@Test
	void create_highIncident_flashesThatTheSupervisorsWereNotified() throws Exception {
		when(incidentService.report(eq(SITE_ID), any())).thenReturn(ID);

		mvc.perform(form(post("/sites/{id}/incidents", SITE_ID), "CRITICAL", "Shock")
			.param("rootCause", "x").param("witnesses", "None").param("medicalTreatment", "None")
			.param("authorityNotified", "false"))
			.andExpect(redirectedUrl("/incidents/30"))
			.andExpect(flash().attribute(Flash.SUCCESS, "incident.flash.reported.high"));
	}

	@Test
	void create_serviceFieldErrors_landOnTheFields() throws Exception {
		when(incidentService.report(eq(SITE_ID), any()))
			.thenThrow(new InvalidFieldException("dailyLogId", "validation.incident.dailyLog.invalid"));

		mvc.perform(lowForm(post("/sites/{id}/incidents", SITE_ID)))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode(IncidentController.FORM, "dailyLogId",
					"validation.incident.dailyLog.invalid"));
	}

	@Test
	void create_serviceSeverityCheck_landsOnTheFields() throws Exception {
		when(incidentService.report(eq(SITE_ID), any()))
			.thenThrow(new MissingSeverityFieldsException(null, List.of("witnesses")));

		mvc.perform(lowForm(post("/sites/{id}/incidents", SITE_ID)))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode(IncidentController.FORM, "witnesses",
					"validation.incident.witnesses.required"));
	}

	// ------------------------------------------------------------------ edit

	@Test
	void editForm_isFilledFromTheIncident() throws Exception {
		when(incidentService.getForEdit(ID)).thenReturn(details(Severity.LOW, IncidentStatus.REPORTED, List.of()));

		mvc.perform(get("/incidents/{id}/edit", ID))
			.andExpect(status().isOk())
			.andExpect(model().attribute("formAction", "/incidents/30"))
			.andExpect(content().string(containsString("value=\"Fall from slab edge\"")));
	}

	@Test
	void update_redirectsWithTheVersionOfTheForm() throws Exception {
		when(incidentService.getForEdit(ID)).thenReturn(details(Severity.LOW, IncidentStatus.REPORTED, List.of()));

		mvc.perform(lowForm(post("/incidents/{id}", ID)).param("version", "4"))
			.andExpect(redirectedUrl("/incidents/30"))
			.andExpect(flash().attribute(Flash.SUCCESS, "incident.flash.updated"));
		verify(incidentService).update(eq(ID), eq(4L), any());
	}

	@Test
	void update_serviceErrors_reRenderTheForm() throws Exception {
		when(incidentService.getForEdit(ID)).thenReturn(details(Severity.LOW, IncidentStatus.REPORTED, List.of()));
		doThrow(new InvalidFieldException("severity", "validation.incident.severity.downgrade"))
			.when(incidentService).update(eq(ID), eq(4L), any());

		mvc.perform(lowForm(post("/incidents/{id}", ID)).param("version", "4"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode(IncidentController.FORM, "severity",
					"validation.incident.severity.downgrade"));
	}

	@Test
	void update_staleVersion_isThe409Page() throws Exception {
		when(incidentService.getForEdit(ID)).thenReturn(details(Severity.LOW, IncidentStatus.REPORTED, List.of()));
		doThrow(new ObjectOptimisticLockingFailureException(Incident.class, ID))
			.when(incidentService).update(eq(ID), eq(3L), any());

		mvc.perform(lowForm(post("/incidents/{id}", ID)).param("version", "3"))
			.andExpect(status().isConflict())
			.andExpect(model().attribute("reloadUrl", "/incidents/30"));
	}

	// ------------------------------------------------------------------ workflow

	@Test
	void startReview_signOff_close_redirectToTheDetailWithAFlash() throws Exception {
		mvc.perform(post("/incidents/{id}/start-review", ID).with(csrf()).param("version", "4"))
			.andExpect(redirectedUrl("/incidents/30"))
			.andExpect(flash().attribute(Flash.SUCCESS, "incident.flash.reviewStarted"));
		mvc.perform(post("/incidents/{id}/sign-off", ID).with(csrf()).param("version", "5"))
			.andExpect(flash().attribute(Flash.SUCCESS, "incident.flash.signedOff"));
		mvc.perform(post("/incidents/{id}/close", ID).with(csrf()).param("version", "6"))
			.andExpect(flash().attribute(Flash.SUCCESS, "incident.flash.closed"));
		verify(incidentService).startReview(ID, 4L);
		verify(incidentService).signOff(ID, 5L);
		verify(incidentService).close(ID, 6L);
	}

	/** AR2: a site manager signing off a HIGH / CRITICAL incident - the service refuses, the screen answers 403. */
	@Test
	@WithMockUser(roles = "SITE_MANAGER")
	void signOff_ofAHighIncidentBySiteManager_is403() throws Exception {
		doThrow(new AccessDeniedException("supervisor only")).when(incidentService).signOff(ID, 5L);

		mvc.perform(post("/incidents/{id}/sign-off", ID).with(csrf()).param("version", "5"))
			.andExpect(status().isForbidden());
	}

	@Test
	void signOff_withMissingFields_redirectsBackWithTheErrorFlash() throws Exception {
		doThrow(new MissingSeverityFieldsException(ID, List.of("rootCause"))).when(incidentService).signOff(ID, 5L);

		mvc.perform(post("/incidents/{id}/sign-off", ID).with(csrf()).param("version", "5")
			.header("Referer", "http://localhost/incidents/30"))
			.andExpect(redirectedUrl("/incidents/30"))
			.andExpect(flash().attribute(Flash.ERROR, MissingSeverityFieldsException.MESSAGE_KEY));
	}

	@Test
	void close_withOpenActions_redirectsBackWithTheErrorFlash() throws Exception {
		doThrow(new InvalidTransitionException(IncidentService.CLOSE_OPEN_ACTIONS, "open")).when(incidentService)
			.close(ID, 6L);

		mvc.perform(post("/incidents/{id}/close", ID).with(csrf()).param("version", "6")
			.header("Referer", "http://localhost/incidents/30"))
			.andExpect(flash().attribute(Flash.ERROR, IncidentService.CLOSE_OPEN_ACTIONS));
	}

	@Test
	void delete_goesToTheSitesIncidentList_orBackWithTheReason() throws Exception {
		when(incidentService.delete(ID, 4L)).thenReturn(SITE_ID);
		when(incidentService.delete(ID, 5L)).thenThrow(new DeletionNotAllowedException(IncidentService.DELETE_BLOCKED, "x"));

		mvc.perform(post("/incidents/{id}/delete", ID).with(csrf()).param("version", "4"))
			.andExpect(redirectedUrl("/sites/1/incidents"))
			.andExpect(flash().attribute(Flash.SUCCESS, "incident.flash.deleted"));
		mvc.perform(post("/incidents/{id}/delete", ID).with(csrf()).param("version", "5"))
			.andExpect(redirectedUrl("/incidents/30"))
			.andExpect(flash().attribute(Flash.ERROR, IncidentService.DELETE_BLOCKED));
	}

	@Test
	void post_withoutCsrf_isRefused() throws Exception {
		mvc.perform(post("/incidents/{id}/sign-off", ID).param("version", "5")).andExpect(status().isForbidden());
		verify(incidentService, never()).signOff(anyLong(), anyLong());
	}

}
