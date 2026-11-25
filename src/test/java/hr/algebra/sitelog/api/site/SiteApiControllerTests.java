package hr.algebra.sitelog.api.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.site.service.SiteCommand;
import hr.algebra.sitelog.site.service.SiteDetails;
import hr.algebra.sitelog.site.service.SiteListItem;
import hr.algebra.sitelog.site.service.SiteService;
import hr.algebra.sitelog.site.service.SiteStatusFilter;
import hr.algebra.sitelog.support.WebSliceConfig;
import hr.algebra.sitelog.support.WebSliceTest;

@WebMvcTest(SiteApiController.class)
@Import(WebSliceConfig.class)
@WithMockUser(roles = "ADMIN")
class SiteApiControllerTests extends WebSliceTest {

	static final String VALID = """
			{"code":"zg-04","name":"Tower D","address":"Ilica 1, Zagreb","startDate":"2026-03-01","managerId":7}""";

	@MockitoBean
	SiteService siteService;

	private static SiteDetails details(Long id) {
		return new SiteDetails(id, "ZG-04", "Tower D", "Ilica 1, Zagreb", null, LocalDate.of(2026, 3, 1), null, 7L,
				"Marko Kovacevic", false, 2, 1, 0, 0, LocalDate.of(2026, 11, 14));
	}

	private static SiteListItem item(Long id) {
		return new SiteListItem(id, "ZG-04", "Tower D", "Ilica 1, Zagreb", LocalDate.of(2026, 3, 1), null,
				"Marko Kovacevic", false);
	}

	// ---------------------------------------------------------------- GET

	@Test
	void list_returnsAPageSortedByNameByDefault() throws Exception {
		when(siteService.list(any(), any(), any()))
			.thenAnswer(call -> new PageImpl<>(List.of(item(3L)), call.getArgument(2, Pageable.class), 41));

		mvc.perform(get("/api/v1/sites"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.content[0].id").value(3))
			.andExpect(jsonPath("$.content[0].managerName").value("Marko Kovacevic"))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalElements").value(41))
			.andExpect(jsonPath("$.totalPages").value(3))
			.andExpect(jsonPath("$.sort[0]").value("name,ASC"));
	}

	@Test
	void list_passesFilterStatusAndAllowedSortAndCapsTheSize() throws Exception {
		when(siteService.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

		mvc.perform(get("/api/v1/sites?q=tower&status=FINISHED&page=2&size=500&sort=startDate,desc"))
			.andExpect(status().isOk());

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(siteService).list(eq("tower"), eq(SiteStatusFilter.FINISHED), pageable.capture());
		assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
		assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
		assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "startDate"));
	}

	@Test
	void list_unknownSortProperty_is400() throws Exception {
		mvc.perform(get("/api/v1/sites?sort=manager.passwordHash"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("validation.page.invalid"))
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("code, name, startDate")));
		verifyNoInteractions(siteService);
	}

	@Test
	void list_unknownStatus_is400() throws Exception {
		mvc.perform(get("/api/v1/sites?status=PAUSED")).andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(roles = "INSPECTOR")
	void get_returnsTheSiteForAnySignedInUser() throws Exception {
		when(siteService.get(3L)).thenReturn(details(3L));

		mvc.perform(get("/api/v1/sites/3"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ZG-04"))
			.andExpect(jsonPath("$.startDate").value("2026-03-01"))
			.andExpect(jsonPath("$.dailyLogCount").value(2))
			.andExpect(jsonPath("$.logsAwaitingSignOff").value(1));
	}

	@Test
	void get_unknownSite_is404Problem() throws Exception {
		when(siteService.get(99L)).thenThrow(new NotFoundException(Site.class, 99L));

		mvc.perform(get("/api/v1/sites/99"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.instance").value("/api/v1/sites/99"));
	}

	@Test
	void anonymous_is401NotARedirectToLogin() throws Exception {
		mvc.perform(get("/api/v1/sites").with(anonymous()))
			.andExpect(status().isUnauthorized())
			.andExpect(header().doesNotExist("Location"))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	// ---------------------------------------------------------------- POST

	@Test
	void create_is201WithLocationAndBody() throws Exception {
		when(siteService.create(any())).thenReturn(12L);
		when(siteService.get(12L)).thenReturn(details(12L));

		mvc.perform(post("/api/v1/sites").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "http://localhost/api/v1/sites/12"))
			.andExpect(jsonPath("$.id").value(12));

		ArgumentCaptor<SiteCommand> command = ArgumentCaptor.forClass(SiteCommand.class);
		verify(siteService).create(command.capture());
		assertThat(command.getValue().code()).isEqualTo("ZG-04");
		assertThat(command.getValue().managerId()).isEqualTo(7L);
	}

	@Test
	void create_invalidBody_is400WithEveryField() throws Exception {
		mvc.perform(post("/api/v1/sites").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
				{"code":"","name":"Tower D","address":"Ilica 1","startDate":"2026-03-01","endDate":"2026-01-01"}"""))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors.length()").value(3))
			.andExpect(jsonPath("$.errors[?(@.field == 'code')].code").value("validation.site.code.required"))
			.andExpect(jsonPath("$.errors[?(@.field == 'managerId')].message").value("Select a site manager"))
			.andExpect(jsonPath("$.errors[?(@.field == 'endDate')].code").value("validation.site.endDate.beforeStart"));
		verify(siteService, never()).create(any());
	}

	@Test
	void create_duplicateCode_is422() throws Exception {
		when(siteService.create(any())).thenThrow(new InvalidFieldException("code", SiteService.CODE_DUPLICATE));

		mvc.perform(post("/api/v1/sites").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errors[0].field").value("code"))
			.andExpect(jsonPath("$.errors[0].code").value(SiteService.CODE_DUPLICATE));
	}

	@Test
	@WithMockUser(roles = "SITE_MANAGER")
	void create_refusedByTheService_is403Problem() throws Exception {
		when(siteService.create(any()))
			.thenThrow(new AuthorizationDeniedException("Access Denied", new AuthorizationDecision(false)));

		mvc.perform(post("/api/v1/sites").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void create_withoutCsrfToken_is403AndTheServiceIsNotCalled() throws Exception {
		mvc.perform(post("/api/v1/sites").contentType(MediaType.APPLICATION_JSON).content(VALID))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("error.csrf"));
		verifyNoInteractions(siteService);
	}

	// ---------------------------------------------------------------- PUT / DELETE

	@Test
	void update_is200WithTheUpdatedSite() throws Exception {
		when(siteService.get(3L)).thenReturn(details(3L));

		mvc.perform(put("/api/v1/sites/3").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(3));

		verify(siteService).update(eq(3L), any(SiteCommand.class));
	}

	@Test
	void update_unknownSite_is404() throws Exception {
		doThrow(new NotFoundException(Site.class, 99L)).when(siteService).update(eq(99L), any());

		mvc.perform(put("/api/v1/sites/99").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(VALID))
			.andExpect(status().isNotFound());
	}

	@Test
	void delete_is204WithoutBody() throws Exception {
		mvc.perform(delete("/api/v1/sites/3").with(csrf()))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));

		verify(siteService).delete(3L);
	}

	@Test
	void delete_siteWithRecords_is409() throws Exception {
		doThrow(new DeletionNotAllowedException(SiteService.DELETE_BLOCKED, "records")).when(siteService).delete(3L);

		mvc.perform(delete("/api/v1/sites/3").with(csrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(SiteService.DELETE_BLOCKED));
	}

	@Test
	void list_withoutQuery_passesNullFilter() throws Exception {
		when(siteService.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

		mvc.perform(get("/api/v1/sites")).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());

		verify(siteService).list(isNull(), eq(SiteStatusFilter.ALL), any());
	}

}
