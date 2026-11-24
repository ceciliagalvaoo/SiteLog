package hr.algebra.sitelog.user.web;

import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.web.Flash;
import hr.algebra.sitelog.user.service.PasswordPolicy;
import hr.algebra.sitelog.user.service.UserService;
import hr.algebra.sitelog.user.service.UserView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * "My profile" for every signed-in user: account details with the preferred language, and the
 * password change. Two forms on one page, each with its own POST and PRG redirect.
 *
 * <p>Model of {@code user/profile}: {@code profile} ({@link UserView}), {@code profileForm},
 * {@code passwordForm}, {@code languages} ({@code ["en","hr"]}), {@code passwordMinLength}.
 */
@Controller
@RequestMapping("/profile")
public class ProfileController {

	static final String VIEW = "user/profile";

	private final UserService userService;

	private final LocaleResolver localeResolver;

	public ProfileController(UserService userService, LocaleResolver localeResolver) {
		this.userService = userService;
		this.localeResolver = localeResolver;
	}

	@GetMapping
	public String show(Model model) {
		UserView profile = userService.getProfile();
		return render(model, profile, ProfileForm.from(profile), new PasswordForm());
	}

	/** Saves name and language; the new language applies at once (locale cookie). */
	@PostMapping
	public String update(@Validated @ModelAttribute("profileForm") ProfileForm form, BindingResult result,
			Model model, RedirectAttributes redirect, HttpServletRequest request, HttpServletResponse response) {
		if (!result.hasErrors()) {
			try {
				userService.updateProfile(form.toCommand());
				localeResolver.setLocale(request, response, Locale.forLanguageTag(form.getPreferredLanguage()));
				Flash.success(redirect, "profile.flash.updated");
				return "redirect:/profile";
			}
			catch (InvalidFieldException e) {
				result.rejectValue(e.getField(), e.getMessageKey());
			}
		}
		return render(model, userService.getProfile(), form, new PasswordForm());
	}

	@PostMapping("/password")
	public String changePassword(@Validated @ModelAttribute("passwordForm") PasswordForm form, BindingResult result,
			Model model, RedirectAttributes redirect) {
		if (!result.hasErrors()) {
			try {
				userService.changePassword(form.toCommand());
				Flash.success(redirect, "profile.flash.passwordChanged");
				return "redirect:/profile";
			}
			catch (InvalidFieldException e) {
				result.rejectValue(e.getField(), e.getMessageKey());
			}
		}
		form.clear();
		UserView profile = userService.getProfile();
		return render(model, profile, ProfileForm.from(profile), form);
	}

	private static String render(Model model, UserView profile, ProfileForm profileForm, PasswordForm passwordForm) {
		model.addAttribute("profile", profile);
		model.addAttribute("profileForm", profileForm);
		model.addAttribute("passwordForm", passwordForm);
		model.addAttribute("languages", UserService.LANGUAGES);
		model.addAttribute("passwordMinLength", PasswordPolicy.MIN_LENGTH);
		model.addAttribute("contextTitle", profile.fullName());
		return VIEW;
	}

}
