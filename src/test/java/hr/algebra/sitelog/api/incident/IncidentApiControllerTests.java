package hr.algebra.sitelog.api.incident;

import static hr.algebra.sitelog.api.incident.IncidentApiFixtures.REPORT_BODY;
import static hr.algebra.sitelog.api.incident.IncidentApiFixtures.UPDATE_BODY;
import static hr.algebra.sitelog.api.incident.IncidentApiFixtures.incident;
import static hr.algebra.sitelog.api.incident.IncidentApiFixtures.row;
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

import java.time.Instant;
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
import hr.algebra.sitelog.common.exception.MissingSeverityFieldsException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.incident.domain.Incident;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.domain.Severity;
import hr.algebra.sitelog.incident.service.IncidentCommand;
import hr.algebra.sitelog.incident.service.IncidentFilter;
import hr.algebra.sitelog.incident.service.IncidentService;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.site.service.SiteService;
import hr.algebra.sitelog.support.WebSliceConfig;
import hr.algebra.sitelog.support.WebSliceTest;

@WebMvcTest(IncidentApiController.class)
@Import(WebSliceConfig.class)
@WithMockUser(roles = "SITE_MANAGER")
class IncidentApiControllerTests extends WebSliceTest {

	static final String VERSION_2 = "{\"version\":2}";

	@MockitoBean
	IncidentService incidentService;

	@MockitoBean
	SiteService siteService;

	private static AuthorizationDeniedException denied() {
		return new AuthorizationDeniedException("Access Denied", new AuthorizationDecision(false));
	}

	// ---------------------------------------------------------------- collections

	@Test
	void listForSite_passesTheFiltersAndSortsNewestFirst() throws Exception {
		when(incidentService.list(any(), any())).thenAnswer(
				call -> new PageImpl<>(List.of(row(12L)), call.getArgument(1, Pageable.class), 1));

		mvc.perform(get("/api/v1/sites/3/incidents?status=REPORTED&severity=HIGH"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(12))
			.andExpect(jsonPath("$.content[0].severity").value("HIGH"))
			.andExpect(jsonPath("$.content[0].occurredAt").value("2026-11-20T09:30:00+01:00"))
			.andExpect(jsonPath("$.sort[0]").value("occurredAt,DESC"));

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(incidentService).list(eq(new IncidentFilter(3L, IncidentStatus.REPORTED, Severity.HIGH)),
				pageable.capture());
		assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "occurredAt"));
	}

	@Test
	void listForSite_unknownSite_is404() throws Exception {
		when(siteService.getSummary(99L)).thenThrow(new NotFoundException(Site.class, 99L));

		mvc.perform(get("/api/v1/sites/99/incidents"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		verify(incidentService, never()).list(any(), any());
	}

	@Test
	void listAll_withOptionalSiteAndAllowedSort() throws Exception {
		when(incidentService.list(any(), any())).thenAnswer(
				call -> new PageImpl<>(List.of(), call.getArgument(1, Pageable.class), 0));

		mvc.perform(get("/api/v1/incidents?siteId=4&sort=title,asc")).andExpect(status().isOk());

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(incidentService).list(eq(new IncidentFilter(4L, null, null)), pageable.capture());
		assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("title"));
	}

	@Test
	void list_unknownSortOrFilterValue_is400() throws Exception {
		mvc.perform(get("/api/v1/incidents?sort=description"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation.page.invalid"));
		mvc.perform(get("/api/v1/incidents?severity=EXTREME"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		verify(incidentService, never()).list(any(), any());
	}

	// ---------------------------------------------------------------- report

	@Test
	void report_is201WithLocationAndTheIncident() throws Exception {
		when(incidentService.report(eq(3L), any())).thenReturn(12L);
		when(incidentService.getDetail(12L)).thenReturn(incident(12L, Severity.HIGH, IncidentStatus.REPORTED, 0, List.of()));

		mvc.perform(post("/api/v1/sites/3/incidents").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(REPORT_BODY))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "http://localhost/api/v1/incidents/12"))
			.andExpect(jsonPath("$.status").value("REPORTED"))
			.andExpect(jsonPath("$.requiresSupervisorSignOff").value(true))
			.andExpect(jsonPath("$.affectedCrews[0].name").value("Concrete crew A"))
			.andExpect(jsonPath("$.correctiveActions[0].incidentVersion").value(0))
			.andExpect(jsonPath("$.timeline[0].action").value("INCIDENT_REPORTED"));

		ArgumentCaptor<IncidentCommand> command = ArgumentCaptor.forClass(IncidentCommand.class);
		verify(incidentService).report(eq(3L), command.capture());
		assertThat(command.getValue().title()).isEqualTo("Slip on formwork");
		assertThat(command.getValue().occurredAt()).isEqualTo(Instant.parse("2026-11-20T08:30:00Z"));
		assertThat(command.getValue().affectedCrewIds()).containsExactly(1L);
		assertThat(command.getValue().authorityNotified()).isFalse();
	}

	@Test
	void report_highWithoutTheExtraFields_is422ListingEveryMissingField() throws Exception {
		when(incidentService.report(eq(3L), any()))
			.thenThrow(new MissingSeverityFieldsException(null, List.of("rootCause", "authorityNotified")));

		mvc.perform(post("/api/v1/sites/3/incidents").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(REPORT_BODY))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value(MissingSeverityFieldsException.MESSAGE_KEY))
			.andExpect(jsonPath("$.errors.length()").value(2))
			.andExpect(jsonPath("$.errors[0].field").value("rootCause"))
			.andExpect(jsonPath("$.errors[0].code").value("validation.incident.rootCause.required"))
			.andExpect(jsonPath("$.errors[0].message").value("Root cause is required"))
			.andExpect(jsonPath("$.errors[1].field").value("authorityNotified"));
	}

	@Test
	void report_futureDate_is422OnTheField() throws Exception {
		when(incidentService.report(eq(3L), any()))
			.thenThrow(new InvalidFieldException("occurredAt", IncidentService.OCCURRED_FUTURE));

		mvc.perform(post("/api/v1/sites/3/incidents").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(REPORT_BODY))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("occurredAt"));
	}

	@Test
	void report_withoutTitleAndSeverity_is400AndTheServiceIsNotCalled() throws Exception {
		mvc.perform(post("/api/v1/sites/3/incidents").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
				{"occurredAt":"2026-11-20T09:30:00+01:00","description":"Wet deck","category":"SLIP_TRIP"}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field == 'title')].code").value("validation.incident.title.required"))
			.andExpect(jsonPath("$.errors[?(@.field == 'severity')].code").value("validation.incident.severity.required"));
		verify(incidentService, never()).report(anyLong(), any());
	}

	@Test
	@WithMockUser(roles = "INSPECTOR")
	void report_byANonManager_is403() throws Exception {
		when(incidentService.report(eq(3L), any())).thenThrow(denied());

		mvc.perform(post("/api/v1/sites/3/incidents").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(REPORT_BODY))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("error.403.body"));
	}

	@Test
	void report_withoutCsrf_is403AndNothingIsReported() throws Exception {
		mvc.perform(post("/api/v1/sites/3/incidents").contentType(MediaType.APPLICATION_JSON).content(REPORT_BODY))
			.andExpect(status().isForbidden());
		verify(incidentService, never()).report(anyLong(), any());
	}

	// ---------------------------------------------------------------- single incident

	@Test
	void get_returnsTheIncidentWithItsVersionAndFlags() throws Exception {
		when(incidentService.getDetail(12L))
			.thenReturn(incident(12L, Severity.HIGH, IncidentStatus.UNDER_REVIEW, 4, List.of("witnesses")));

		mvc.perform(get("/api/v1/incidents/12"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(4))
			.andExpect(jsonPath("$.missingSeverityFields[0]").value("witnesses"))
			.andExpect(jsonPath("$.canSignOff").value(false))
			.andExpect(jsonPath("$.editable").value(true));
	}

	@Test
	void get_unknownIncident_is404() throws Exception {
		when(incidentService.getDetail(99L)).thenThrow(new NotFoundException(Incident.class, 99L));

		mvc.perform(get("/api/v1/incidents/99"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("error.notFound"));
	}

	@Test
	void update_passesTheVersionAndReturnsTheNewState() throws Exception {
		when(incidentService.getDetail(12L)).thenReturn(incident(12L, Severity.LOW, IncidentStatus.REPORTED, 3, List.of()));

		mvc.perform(put("/api/v1/incidents/12").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(3));

		ArgumentCaptor<IncidentCommand> command = ArgumentCaptor.forClass(IncidentCommand.class);
		verify(incidentService).update(eq(12L), eq(2L), command.capture());
		assertThat(command.getValue().severity()).isEqualTo(Severity.LOW);
		assertThat(command.getValue().affectedCrewIds()).isEmpty();
	}

	@Test
	void update_withoutVersion_is400() throws Exception {
		mvc.perform(put("/api/v1/incidents/12").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(UPDATE_BODY.replace("\"version\":2,", "")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("version"));
		verify(incidentService, never()).update(anyLong(), any(), any());
	}

	@Test
	void update_staleVersion_is409WithTheIncidentMessage() throws Exception {
		doThrow(new ObjectOptimisticLockingFailureException(Incident.class, 12L)).when(incidentService)
			.update(eq(12L), eq(2L), any());

		mvc.perform(put("/api/v1/incidents/12").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("incident.flash.conflict"))
			.andExpect(jsonPath("$.detail").value("This incident was changed by someone else. Reload and try again."));
	}

	@Test
	void update_severityLoweredByAManager_is422() throws Exception {
		doThrow(new InvalidFieldException("severity", IncidentService.SEVERITY_DOWNGRADE)).when(incidentService)
			.update(eq(12L), eq(2L), any());

		mvc.perform(put("/api/v1/incidents/12").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("severity"));
	}

	@Test
	void delete_is204AndPassesTheVersion() throws Exception {
		mvc.perform(delete("/api/v1/incidents/12?version=2").with(csrf())).andExpect(status().isNoContent());

		verify(incidentService).delete(12L, 2L);
	}

	@Test
	void delete_withoutVersion_is400() throws Exception {
		mvc.perform(delete("/api/v1/incidents/12").with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		verify(incidentService, never()).delete(anyLong(), any());
	}

	@Test
	void delete_notReported_is409() throws Exception {
		doThrow(new DeletionNotAllowedException(IncidentService.DELETE_BLOCKED, "signed")).when(incidentService)
			.delete(12L, 2L);

		mvc.perform(delete("/api/v1/incidents/12?version=2").with(csrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(IncidentService.DELETE_BLOCKED));
	}

	// ---------------------------------------------------------------- workflow

	@Test
	@WithMockUser(roles = "SAFETY_SUPERVISOR")
	void startReview_is200WithTheIncidentUnderReview() throws Exception {
		when(incidentService.getDetail(12L))
			.thenReturn(incident(12L, Severity.HIGH, IncidentStatus.UNDER_REVIEW, 3, List.of()));

		mvc.perform(post("/api/v1/incidents/12/start-review").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(VERSION_2))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
			.andExpect(jsonPath("$.canSignOff").value(true));

		verify(incidentService).startReview(12L, 2L);
	}

	@Test
	@WithMockUser(roles = "SAFETY_SUPERVISOR")
	void signOff_is200WithWhoAndWhen() throws Exception {
		when(incidentService.getDetail(12L))
			.thenReturn(incident(12L, Severity.HIGH, IncidentStatus.SIGNED_OFF, 3, List.of()));

		mvc.perform(post("/api/v1/incidents/12/sign-off").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(VERSION_2))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SIGNED_OFF"))
			.andExpect(jsonPath("$.signedOffByName").value("Sanja Babic"))
			.andExpect(jsonPath("$.signedOffAt").value("2026-11-20T12:30:00+01:00"));

		verify(incidentService).signOff(12L, 2L);
	}

	@Test
	void signOff_ofAHighIncidentByASiteManager_is403Problem() throws Exception {
		doThrow(denied()).when(incidentService).signOff(12L, 2L);

		mvc.perform(post("/api/v1/incidents/12/sign-off").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(VERSION_2))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("error.403.body"));
	}

	@Test
	@WithMockUser(roles = "SAFETY_SUPERVISOR")
	void signOff_withMissingFields_is422AndNotUnderReview_is409() throws Exception {
		doThrow(new MissingSeverityFieldsException(12L, List.of("witnesses"))).when(incidentService).signOff(12L, 2L);
		mvc.perform(post("/api/v1/incidents/12/sign-off").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(VERSION_2))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("witnesses"));

		doThrow(new InvalidTransitionException(IncidentService.SIGN_OFF_NOT_UNDER_REVIEW, "reported"))
			.when(incidentService).signOff(12L, 3L);
		mvc.perform(post("/api/v1/incidents/12/sign-off").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"version\":3}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(IncidentService.SIGN_OFF_NOT_UNDER_REVIEW));
	}

	@Test
	void signOff_withoutVersion_is400() throws Exception {
		mvc.perform(post("/api/v1/incidents/12/sign-off").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("version"));
		verify(incidentService, never()).signOff(anyLong(), any());
	}

	@Test
	@WithMockUser(roles = "SAFETY_SUPERVISOR")
	void close_withOpenCorrectiveActions_is409() throws Exception {
		doThrow(new InvalidTransitionException(IncidentService.CLOSE_OPEN_ACTIONS, "open")).when(incidentService)
			.close(12L, 2L);

		mvc.perform(post("/api/v1/incidents/12/close").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(VERSION_2))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(IncidentService.CLOSE_OPEN_ACTIONS))
			.andExpect(jsonPath("$.detail").value("Complete every corrective action before closing the incident."));
	}

	@Test
	@WithMockUser(roles = "SAFETY_SUPERVISOR")
	void close_is200() throws Exception {
		when(incidentService.getDetail(12L)).thenReturn(incident(12L, Severity.LOW, IncidentStatus.CLOSED, 5, List.of()));

		mvc.perform(post("/api/v1/incidents/12/close").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(VERSION_2))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CLOSED"));
		verify(incidentService).close(12L, 2L);
	}

}
