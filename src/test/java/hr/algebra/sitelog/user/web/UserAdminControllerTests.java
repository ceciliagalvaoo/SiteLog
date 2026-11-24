package hr.algebra.sitelog.user.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.support.WebSliceConfig;
import hr.algebra.sitelog.support.WebSliceTest;
import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.service.SiteAssignmentOption;
import hr.algebra.sitelog.user.service.UserCommand;
import hr.algebra.sitelog.user.service.UserListItem;
import hr.algebra.sitelog.user.service.UserService;
import hr.algebra.sitelog.user.service.UserView;

@WebMvcTest(UserAdminController.class)
@Import(WebSliceConfig.class)
@WithMockUser(roles = "ADMIN")
class UserAdminControllerTests extends WebSliceTest {

	private static final String STRONG = "correct-horse-battery";

	@MockitoBean
	UserService userService;

	private static UserView manager() {
		return new UserView(7L, "manager@sitelog.test", "Marko Kovacevic", RoleName.SITE_MANAGER, "hr", true, false,
				List.of());
	}

	@Test
	void list_rendersUsers() throws Exception {
		when(userService.list(eq("ana"), any(Pageable.class))).thenReturn(new PageImpl<>(
				List.of(new UserListItem(1L, "admin@sitelog.test", "Ana Horvat", RoleName.ADMIN, "en", true))));

		mvc.perform(get("/admin/users").param("q", "ana"))
			.andExpect(status().isOk())
			.andExpect(view().name(UserAdminController.LIST_VIEW))
			.andExpect(content().string(containsString("Ana Horvat")));
	}

	@Test
	@WithMockUser(roles = "SITE_MANAGER")
	void list_isForbiddenForOtherRoles() throws Exception {
		mvc.perform(get("/admin/users")).andExpect(status().isForbidden());
	}

	@Test
	void newForm_rendersEmptyForm() throws Exception {
		mvc.perform(get("/admin/users/new"))
			.andExpect(status().isOk())
			.andExpect(model().attributeExists("userForm", "roles", "languages"))
			.andExpect(model().attribute("passwordMinLength", 12))
			.andExpect(content().string(containsString("initialPassword")))
			.andExpect(content().string(containsString("At least 12 characters")));
	}

	@Test
	void create_redirectsToTheEditForm() throws Exception {
		when(userService.create(any())).thenReturn(42L);

		mvc.perform(post("/admin/users").with(csrf()).param("email", "iva@sitelog.test").param("fullName", "Iva")
				.param("role", "INSPECTOR").param("preferredLanguage", "en").param("initialPassword", STRONG))
			.andExpect(redirectedUrl("/admin/users/42/edit"))
			.andExpect(flash().attribute("flashSuccess", "user.flash.created"));
		verify(userService).create(new UserCommand("iva@sitelog.test", "Iva", RoleName.INSPECTOR, "en", true, STRONG));
	}

	@Test
	void create_invalidInput_reRendersWithoutThePassword() throws Exception {
		mvc.perform(post("/admin/users").with(csrf()).param("email", "not-an-email").param("fullName", "")
				.param("preferredLanguage", "en").param("initialPassword", "short"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("userForm", "email", "fullName", "role", "initialPassword"))
			.andExpect(content().string(org.hamcrest.Matchers.not(containsString("value=\"short\""))));
		verify(userService, never()).create(any());
	}

	@Test
	void create_duplicateEmail_isShownOnTheField() throws Exception {
		when(userService.create(any())).thenThrow(new InvalidFieldException("email", UserService.EMAIL_DUPLICATE));

		mvc.perform(post("/admin/users").with(csrf()).param("email", "admin@sitelog.test").param("fullName", "Ana")
				.param("role", "ADMIN").param("preferredLanguage", "en").param("initialPassword", STRONG))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode("userForm", "email", UserService.EMAIL_DUPLICATE));
	}

	@Test
	void editForm_showsTheSiteAssignment() throws Exception {
		when(userService.get(7L)).thenReturn(manager());
		when(userService.siteOptions(7L))
			.thenReturn(List.of(new SiteAssignmentOption(3L, "ZG-01", "Riverside Tower", "Marko Kovacevic", true)));

		mvc.perform(get("/admin/users/7/edit"))
			.andExpect(status().isOk())
			.andExpect(model().attributeExists("user", "siteOptions", "siteAssignmentForm"))
			.andExpect(content().string(containsString("Riverside Tower")));
	}

	@Test
	void update_redirectsToTheList() throws Exception {
		mvc.perform(post("/admin/users/7").with(csrf()).param("email", "manager@sitelog.test")
				.param("fullName", "Marko").param("role", "SITE_MANAGER").param("preferredLanguage", "hr")
				.param("active", "true"))
			.andExpect(redirectedUrl("/admin/users"))
			.andExpect(flash().attribute("flashSuccess", "user.flash.updated"));
		verify(userService).update(7L,
				new UserCommand("manager@sitelog.test", "Marko", RoleName.SITE_MANAGER, "hr", true, null));
	}

	@Test
	void update_ruleBrokenInTheService_isShownOnTheField() throws Exception {
		doThrow(new InvalidFieldException("role", UserService.ROLE_MANAGES_SITES)).when(userService)
			.update(eq(7L), any());
		when(userService.get(7L)).thenReturn(manager());

		mvc.perform(post("/admin/users/7").with(csrf()).param("email", "manager@sitelog.test")
				.param("fullName", "Marko").param("role", "INSPECTOR").param("preferredLanguage", "hr"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode("userForm", "role", UserService.ROLE_MANAGES_SITES));
	}

	@Test
	void assignSites_redirectsBackToTheForm() throws Exception {
		mvc.perform(post("/admin/users/7/sites").with(csrf()).param("siteIds", "3", "4"))
			.andExpect(redirectedUrl("/admin/users/7/edit"))
			.andExpect(flash().attribute("flashSuccess", "user.flash.sitesAssigned"));
		verify(userService).assignSites(7L, List.of(3L, 4L));
	}

	@Test
	void assignSites_refused_reRendersWithTheError() throws Exception {
		doThrow(new InvalidFieldException("siteIds", UserService.SITES_UNASSIGN)).when(userService)
			.assignSites(eq(7L), any());
		when(userService.get(7L)).thenReturn(manager());

		mvc.perform(post("/admin/users/7/sites").with(csrf()))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode("siteAssignmentForm", "siteIds", UserService.SITES_UNASSIGN));
	}

	@Test
	void deactivate_andActivate() throws Exception {
		when(userService.setActive(5L, false)).thenReturn("Tomislav Novak");
		when(userService.setActive(5L, true)).thenReturn("Tomislav Novak");

		mvc.perform(post("/admin/users/5/deactivate").with(csrf()))
			.andExpect(redirectedUrl("/admin/users"))
			.andExpect(flash().attribute("flashSuccess", "user.flash.deactivated"));
		mvc.perform(post("/admin/users/5/activate").with(csrf()))
			.andExpect(flash().attribute("flashSuccess", "user.flash.activated"));
	}

	@Test
	void deactivateSelf_givesAnErrorFlash() throws Exception {
		when(userService.setActive(1L, false))
			.thenThrow(new InvalidTransitionException(UserService.SELF_DEACTIVATE_FLASH, "self"));

		mvc.perform(post("/admin/users/1/deactivate").with(csrf()).header("Referer", "http://localhost/admin/users"))
			.andExpect(redirectedUrl("/admin/users"))
			.andExpect(flash().attribute("flashError", UserService.SELF_DEACTIVATE_FLASH));
	}

}
