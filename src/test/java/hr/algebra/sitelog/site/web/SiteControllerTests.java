package hr.algebra.sitelog.site.web;

import static hr.algebra.sitelog.support.TestEntities.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.hamcrest.Matchers.nullValue;
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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.common.web.Flash;
import hr.algebra.sitelog.dailylog.repository.DailyLogSummary;
import hr.algebra.sitelog.dailylog.service.DailyLogService;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.site.service.SiteCommand;
import hr.algebra.sitelog.site.service.SiteDetails;
import hr.algebra.sitelog.site.service.SiteListItem;
import hr.algebra.sitelog.site.service.SiteService;
import hr.algebra.sitelog.site.service.SiteStatusFilter;
import hr.algebra.sitelog.support.WebSliceConfig;
import hr.algebra.sitelog.support.WebSliceTest;
import hr.algebra.sitelog.user.service.UserOption;

@WebMvcTest(SiteController.class)
@Import(WebSliceConfig.class)
@WithMockUser
class SiteControllerTests extends WebSliceTest {

	@MockitoBean
	SiteService siteService;

	@MockitoBean
	DailyLogService dailyLogService;

	private static SiteDetails details(Long id) {
		return new SiteDetails(id, "ZG-01", "Tower A", "Vukovarska 10", null, LocalDate.of(2026, 1, 1), null, 7L,
				"Marko Kovacevic", false, 1, 1, 0, 0, TODAY);
	}

	private static Page<SiteListItem> onePage() {
		return new PageImpl<>(List.of(new SiteListItem(3L, "ZG-01", "Tower A", "Vukovarska 10",
				LocalDate.of(2026, 1, 1), null, "Marko Kovacevic", false)));
	}

	@Test
	void list_languageSwitcherKeepsTheStatusFilter() throws Exception {
		when(siteService.list(any(), any(), any())).thenReturn(onePage());

		mvc.perform(get("/sites?status=finished"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("href=\"?status=finished&amp;lang=hr\"")))
			.andExpect(content().string(containsString("href=\"?status=finished&amp;lang=en\"")));
	}

	@Test
	void list_parsesStatusFilterAndOnlySortsByWhitelistedProperties() throws Exception {
		when(siteService.list(any(), any(), any())).thenReturn(onePage());

		mvc.perform(get("/sites").param("status", "finished").param("q", "tower").param("sort", "manager.passwordHash")
			.param("size", "1000"))
			.andExpect(status().isOk())
			.andExpect(view().name(SiteController.LIST_VIEW))
			.andExpect(model().attribute("status", SiteStatusFilter.FINISHED))
			.andExpect(content().string(containsString("Tower A")));

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(siteService).list(eq("tower"), eq(SiteStatusFilter.FINISHED), pageable.capture());
		assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("name"));
		assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
	}

	@Test
	void list_withUnknownStatus_fallsBackToAll() throws Exception {
		when(siteService.list(any(), any(), any())).thenReturn(onePage());

		mvc.perform(get("/sites").param("status", "deleted"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("status", SiteStatusFilter.ALL));
	}

	@Test
	void detail_withUnknownTab_showsOverview() throws Exception {
		when(siteService.get(3L)).thenReturn(details(3L));
		when(dailyLogService.listForSite(eq(3L), any()))
			.thenReturn(new PageImpl<DailyLogSummary>(List.of()));

		mvc.perform(get("/sites/3").param("tab", "../../etc"))
			.andExpect(status().isOk())
			.andExpect(view().name(SiteController.DETAIL_VIEW))
			.andExpect(model().attribute("tab", "overview"))
			.andExpect(model().attribute("contextTitle", "Tower A"));
	}

	@Test
	void detail_whenSiteMissing_rendersOwn404Page() throws Exception {
		when(siteService.get(99L)).thenThrow(new NotFoundException(Site.class, 99L));

		mvc.perform(get("/sites/99"))
			.andExpect(status().isNotFound())
			.andExpect(view().name("error/404"))
			.andExpect(model().attribute("errorMessage", "error.notFound"));
	}

	@Test
	void create_withValidForm_redirectsToTheNewSiteWithSuccessFlash() throws Exception {
		when(siteService.create(any())).thenReturn(42L);

		mvc.perform(post("/sites").with(csrf())
			.param("code", "zg-02").param("name", "Tower B").param("address", "Ilica 1")
			.param("startDate", "2026-03-01").param("managerId", "7"))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/sites/42"))
			.andExpect(flash().attribute(Flash.SUCCESS, "site.flash.created"))
			.andExpect(flash().attribute(Flash.DETAIL, nullValue()))
			.andExpect(flash().attribute(Flash.ARGS, org.hamcrest.Matchers.arrayContaining("Tower B")));

		ArgumentCaptor<SiteCommand> command = ArgumentCaptor.forClass(SiteCommand.class);
		verify(siteService).create(command.capture());
		assertThat(command.getValue().code()).isEqualTo("ZG-02");
		assertThat(command.getValue().managerId()).isEqualTo(7L);
	}

	@Test
	void create_withEndDateBeforeStartDate_reRendersFormWithErrorOnEndDate() throws Exception {
		when(siteService.managerOptions()).thenReturn(List.of(new UserOption(7L, "Marko Kovacevic", "m@x")));

		mvc.perform(post("/sites").with(csrf())
			.param("code", "ZG-02").param("name", "Tower B").param("address", "Ilica 1")
			.param("startDate", "2026-03-01").param("endDate", "2026-02-01").param("managerId", "7"))
			.andExpect(status().isOk())
			.andExpect(view().name(SiteController.FORM_VIEW))
			.andExpect(model().attributeHasFieldErrors("siteForm", "endDate"))
			.andExpect(model().attributeExists("managers"))
			.andExpect(content().string(containsString("End date cannot be before the start date")));
		verify(siteService, never()).create(any());
	}

	@Test
	void create_whenCodeIsTaken_showsTheServiceErrorOnTheCodeField() throws Exception {
		when(siteService.create(any())).thenThrow(new InvalidFieldException("code", SiteService.CODE_DUPLICATE));

		mvc.perform(post("/sites").with(csrf())
			.param("code", "ZG-01").param("name", "Tower B").param("address", "Ilica 1")
			.param("startDate", "2026-03-01").param("managerId", "7"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode("siteForm", "code", SiteService.CODE_DUPLICATE))
			.andExpect(content().string(containsString("Code is already used")));
	}

	@Test
	void update_withInvalidManager_showsErrorOnManagerAndKeepsSiteId() throws Exception {
		doThrow(new InvalidFieldException("managerId", SiteService.MANAGER_INVALID)).when(siteService)
			.update(eq(3L), any());

		mvc.perform(post("/sites/3").with(csrf())
			.param("code", "ZG-01").param("name", "Tower A").param("address", "Ilica 1")
			.param("startDate", "2026-03-01").param("managerId", "9"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode("siteForm", "managerId", SiteService.MANAGER_INVALID))
			.andExpect(model().attribute("siteId", 3L));
	}

	@Test
	void update_withValidForm_redirectsToTheSite() throws Exception {
		mvc.perform(post("/sites/3").with(csrf())
			.param("code", "ZG-01").param("name", "Tower A").param("address", "Ilica 1")
			.param("startDate", "2026-03-01").param("managerId", "7"))
			.andExpect(redirectedUrl("/sites/3"))
			.andExpect(flash().attribute(Flash.SUCCESS, "site.flash.updated"))
			.andExpect(flash().attribute(Flash.DETAIL, nullValue()))
			.andExpect(flash().attribute(Flash.ARGS, org.hamcrest.Matchers.arrayContaining("Tower A")));
	}

	@Test
	void newForm_rendersEmptyFormWithManagers() throws Exception {
		when(siteService.managerOptions()).thenReturn(List.of(new UserOption(7L, "Marko Kovacevic", "m@x")));

		mvc.perform(get("/sites/new"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("siteId", nullValue()))
			.andExpect(content().string(containsString("Marko Kovacevic")));
	}

	@Test
	void delete_ofSiteWithRecords_redirectsBackToTheSiteWithErrorFlash() throws Exception {
		doThrow(new DeletionNotAllowedException(SiteService.DELETE_BLOCKED, "has records")).when(siteService).delete(3L);

		mvc.perform(post("/sites/3/delete").with(csrf()))
			.andExpect(redirectedUrl("/sites/3"))
			.andExpect(flash().attribute(Flash.ERROR, SiteService.DELETE_BLOCKED));
	}

	@Test
	void delete_ofEmptySite_redirectsToTheListWithSuccessFlash() throws Exception {
		mvc.perform(post("/sites/3/delete").with(csrf()))
			.andExpect(redirectedUrl("/sites"))
			.andExpect(flash().attribute(Flash.SUCCESS, "site.flash.deleted"));
		verify(siteService).delete(3L);
	}

}
