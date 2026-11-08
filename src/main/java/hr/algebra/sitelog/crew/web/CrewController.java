package hr.algebra.sitelog.crew.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.web.Flash;
import hr.algebra.sitelog.crew.service.CrewService;

/**
 * Crew screens (Admin): list, create, edit, delete. Thin: bind + validate the form, call one
 * service method, redirect with a flash message (PRG) or re-render the form with its errors.
 */
@Controller
@RequestMapping("/crews")
public class CrewController {

	static final String LIST_VIEW = "crew/list";

	static final String FORM_VIEW = "crew/form";

	private final CrewService crewService;

	public CrewController(CrewService crewService) {
		this.crewService = crewService;
	}

	@GetMapping
	public String list(Model model) {
		model.addAttribute("crews", crewService.list());
		return LIST_VIEW;
	}

	@GetMapping("/new")
	public String newForm(Model model) {
		model.addAttribute("crewForm", new CrewForm());
		model.addAttribute("crewId", null);
		return FORM_VIEW;
	}

	@PostMapping
	public String create(@Validated @ModelAttribute("crewForm") CrewForm form, BindingResult result,
			Model model, RedirectAttributes redirect) {
		if (!result.hasErrors()) {
			try {
				crewService.create(form.toCommand());
				Flash.success(redirect, "crew.flash.created", form.getName());
				return "redirect:/crews";
			}
			catch (InvalidFieldException e) {
				result.rejectValue(e.getField(), e.getMessageKey());
			}
		}
		model.addAttribute("crewId", null);
		return FORM_VIEW;
	}

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		model.addAttribute("crewForm", CrewForm.from(crewService.get(id)));
		model.addAttribute("crewId", id);
		return FORM_VIEW;
	}

	@PostMapping("/{id}")
	public String update(@PathVariable Long id, @Validated @ModelAttribute("crewForm") CrewForm form,
			BindingResult result, Model model, RedirectAttributes redirect) {
		if (!result.hasErrors()) {
			try {
				crewService.update(id, form.toCommand());
				Flash.success(redirect, "crew.flash.updated", form.getName());
				return "redirect:/crews";
			}
			catch (InvalidFieldException e) {
				result.rejectValue(e.getField(), e.getMessageKey());
			}
		}
		model.addAttribute("crewId", id);
		return FORM_VIEW;
	}

	/** Delete (confirmed in a modal). A crew in use stays and the user is told to deactivate it. */
	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, RedirectAttributes redirect) {
		try {
			crewService.delete(id);
			Flash.success(redirect, "crew.flash.deleted");
		}
		catch (DeletionNotAllowedException e) {
			Flash.error(redirect, e.getMessageKey());
		}
		return "redirect:/crews";
	}

}
