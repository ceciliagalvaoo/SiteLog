package hr.algebra.sitelog.user.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.support.WebSliceConfig;
import hr.algebra.sitelog.support.WebSliceTest;
import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.service.ManagedSite;
import hr.algebra.sitelog.user.service.PasswordChangeCommand;
import hr.algebra.sitelog.user.service.ProfileCommand;
import hr.algebra.sitelog.user.service.UserService;
import hr.algebra.sitelog.user.service.UserView;

@WebMvcTest(ProfileController.class)
@Import(WebSliceConfig.class)
@WithMockUser
class ProfileControllerTests extends WebSliceTest {

	private static final String STRONG = "correct-horse-battery";

	@MockitoBean
	UserService userService;

	@BeforeEach
	void profile() {
		when(userService.getProfile()).thenReturn(new UserView(7L, "manager@sitelog.test", "Marko Kovacevic",
				RoleName.SITE_MANAGER, "en", true, true, List.of(new ManagedSite(3L, "ZG-01", "Riverside Tower"))));
	}

	@Test
	void show_rendersBothForms() throws Exception {
		mvc.perform(get("/profile"))
			.andExpect(status().isOk())
			.andExpect(view().name(ProfileController.VIEW))
			.andExpect(model().attributeExists("profile", "profileForm", "passwordForm", "languages"))
			.andExpect(content().string(containsString("ZG-01")));
	}

	@Test
	void update_savesAndSwitchesTheLanguage() throws Exception {
		mvc.perform(post("/profile").with(csrf()).param("fullName", "Marko K.").param("preferredLanguage", "hr"))
			.andExpect(redirectedUrl("/profile"))
			.andExpect(flash().attribute("flashSuccess", "profile.flash.updated"))
			.andExpect(cookie().value("lang", "hr"));
		verify(userService).updateProfile(new ProfileCommand("Marko K.", "hr"));
	}

	@Test
	void update_invalidInput_reRendersWithErrors() throws Exception {
		mvc.perform(post("/profile").with(csrf()).param("fullName", "").param("preferredLanguage", "de"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("profileForm", "fullName", "preferredLanguage"));
		verify(userService, never()).updateProfile(any());
	}

	@Test
	void changePassword_success() throws Exception {
		mvc.perform(post("/profile/password").with(csrf()).param("currentPassword", "old-password-123")
				.param("newPassword", STRONG).param("confirmPassword", STRONG))
			.andExpect(redirectedUrl("/profile"))
			.andExpect(flash().attribute("flashSuccess", "profile.flash.passwordChanged"));
		verify(userService).changePassword(new PasswordChangeCommand("old-password-123", STRONG, STRONG));
	}

	@Test
	void changePassword_tooShort_isAFormError() throws Exception {
		mvc.perform(post("/profile/password").with(csrf()).param("currentPassword", "x")
				.param("newPassword", "short").param("confirmPassword", "short"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("passwordForm", "newPassword"));
		verify(userService, never()).changePassword(any());
	}

	@Test
	void changePassword_emptyNewPassword_showsOneMessageOnly() throws Exception {
		mvc.perform(post("/profile/password").with(csrf()).param("currentPassword", "old-password-123")
				.param("newPassword", "").param("confirmPassword", "x"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode("passwordForm", "newPassword", "PasswordLength"))
			.andExpect(result -> org.assertj.core.api.Assertions.assertThat(
					((org.springframework.validation.BindingResult) result.getModelAndView().getModel()
						.get("org.springframework.validation.BindingResult.passwordForm"))
						.getFieldErrorCount("newPassword")).isEqualTo(1))
			.andExpect(content().string(containsString("Password must be between 12 and 72 characters")));
	}

	@Test
	void changePassword_wrongCurrentPassword_isShownOnTheField() throws Exception {
		doThrow(new InvalidFieldException("currentPassword", UserService.CURRENT_PASSWORD_WRONG))
			.when(userService).changePassword(any());

		mvc.perform(post("/profile/password").with(csrf()).param("currentPassword", "guess-guess-guess")
				.param("newPassword", STRONG).param("confirmPassword", STRONG))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode("passwordForm", "currentPassword",
					UserService.CURRENT_PASSWORD_WRONG))
			.andExpect(content().string(containsString("Current password is wrong")))
			.andExpect(content().string(org.hamcrest.Matchers.not(containsString(STRONG))));
	}

	@Test
	void changePassword_withoutCsrf_isForbidden() throws Exception {
		mvc.perform(post("/profile/password").param("currentPassword", "x"))
			.andExpect(status().isForbidden());
		verify(userService, never()).changePassword(any());
	}

}
