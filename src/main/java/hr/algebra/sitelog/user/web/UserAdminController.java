package hr.algebra.sitelog.user.web;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.web.Flash;
import hr.algebra.sitelog.common.web.Pages;
import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.service.PasswordPolicy;
import hr.algebra.sitelog.user.service.SiteAssignmentOption;
import hr.algebra.sitelog.user.service.UserService;
import hr.algebra.sitelog.user.service.UserView;

/**
 * User administration (Admin only: {@code /admin/**} in the filter chain and
 * {@code hasRole('ADMIN')} on {@link UserService}). Thin: form -> service -> redirect + flash.
 *
 * <ul>
 * <li>{@code GET /admin/users?q=&page=&sort=} - list ({@code user/list}): {@code users}
 * (Page of {@code UserListItem}), {@code q}.</li>
 * <li>{@code GET /admin/users/new}, {@code POST /admin/users} - create ({@code user/form} with
 * {@code userForm} = {@link NewUserForm}, {@code userId} = null, {@code passwordMinLength}).</li>
 * <li>{@code GET /admin/users/{id}/edit}, {@code POST /admin/users/{id}} - edit ({@code user/form}
 * with {@code userForm} = {@link UserForm}, {@code userId}, {@code user}, {@code siteOptions},
 * {@code siteAssignmentForm}).</li>
 * <li>{@code POST /admin/users/{id}/sites} - site assignment (field {@code siteIds}).</li>
 * <li>{@code POST /admin/users/{id}/activate} / {@code /deactivate}.</li>
 * </ul>
 * Both forms also get {@code roles} and {@code languages}.
 */
@Controller
@RequestMapping("/admin/users")
public class UserAdminController {

	static final String LIST_VIEW = "user/list";

	static final String FORM_VIEW = "user/form";

	private static final Set<String> SORTABLE = Set.of("fullName", "email", "active");

	private final UserService userService;

	public UserAdminController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public String list(@RequestParam(name = "q", required = false) String filter,
			@PageableDefault(size = 20) Pageable pageable, Model model) {
		model.addAttribute("users", userService.list(filter, Pages.restrict(pageable, SORTABLE, Sort.by("fullName"))));
		model.addAttribute("q", filter);
		return LIST_VIEW;
	}

	@GetMapping("/new")
	public String newForm(Model model) {
		model.addAttribute("userForm", new NewUserForm());
		return renderNew(model);
	}

	@PostMapping
	public String create(@Validated @ModelAttribute("userForm") NewUserForm form, BindingResult result, Model model,
			RedirectAttributes redirect) {
		if (!result.hasErrors()) {
			try {
				Long id = userService.create(form.toCommand());
				Flash.success(redirect, "user.flash.created", form.getFullName());
				return "redirect:/admin/users/" + id + "/edit";
			}
			catch (InvalidFieldException e) {
				result.rejectValue(e.getField(), e.getMessageKey());
			}
		}
		form.setInitialPassword(null);
		return renderNew(model);
	}

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		UserView user = userService.get(id);
		model.addAttribute("userForm", UserForm.from(user));
		return renderEdit(model, user, null);
	}

	@PostMapping("/{id}")
	public String update(@PathVariable Long id, @Validated @ModelAttribute("userForm") UserForm form,
			BindingResult result, Model model, RedirectAttributes redirect) {
		if (!result.hasErrors()) {
			try {
				userService.update(id, form.toCommand());
				Flash.success(redirect, "user.flash.updated", form.getFullName());
				return "redirect:/admin/users";
			}
			catch (InvalidFieldException e) {
				result.rejectValue(e.getField(), e.getMessageKey());
			}
		}
		return renderEdit(model, userService.get(id), null);
	}

	@PostMapping("/{id}/sites")
	public String assignSites(@PathVariable Long id,
			@ModelAttribute("siteAssignmentForm") SiteAssignmentForm form, BindingResult result, Model model,
			RedirectAttributes redirect) {
		try {
			userService.assignSites(id, form.getSiteIds());
			Flash.success(redirect, "user.flash.sitesAssigned");
			return "redirect:/admin/users/" + id + "/edit";
		}
		catch (InvalidFieldException e) {
			result.rejectValue(e.getField(), e.getMessageKey());
		}
		UserView user = userService.get(id);
		model.addAttribute("userForm", UserForm.from(user));
		return renderEdit(model, user, form);
	}

	@PostMapping("/{id}/activate")
	public String activate(@PathVariable Long id, RedirectAttributes redirect) {
		Flash.success(redirect, "user.flash.activated", userService.setActive(id, true));
		return "redirect:/admin/users";
	}

	/** Deactivating oneself is refused by the service ({@code InvalidTransitionException} -> error flash). */
	@PostMapping("/{id}/deactivate")
	public String deactivate(@PathVariable Long id, RedirectAttributes redirect) {
		Flash.success(redirect, "user.flash.deactivated", userService.setActive(id, false));
		return "redirect:/admin/users";
	}

	private static String renderNew(Model model) {
		model.addAttribute("userId", null);
		model.addAttribute("passwordMinLength", PasswordPolicy.MIN_LENGTH);
		addOptions(model);
		return FORM_VIEW;
	}

	private String renderEdit(Model model, UserView user, SiteAssignmentForm submittedSites) {
		List<SiteAssignmentOption> siteOptions = userService.siteOptions(user.id());
		model.addAttribute("userId", user.id());
		model.addAttribute("user", user);
		model.addAttribute("siteOptions", siteOptions);
		if (submittedSites == null) {
			model.addAttribute("siteAssignmentForm", SiteAssignmentForm.from(siteOptions));
		}
		addOptions(model);
		return FORM_VIEW;
	}

	private static void addOptions(Model model) {
		model.addAttribute("roles", RoleName.values());
		model.addAttribute("languages", UserService.LANGUAGES);
	}

}
