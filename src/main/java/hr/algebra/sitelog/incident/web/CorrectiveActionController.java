package hr.algebra.sitelog.incident.web;

import java.time.Clock;
import java.time.LocalDate;

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
import hr.algebra.sitelog.incident.service.CorrectiveActionService;
import hr.algebra.sitelog.incident.service.IncidentHeader;

/**
 * Corrective action form (add / edit), "mark as done" and delete. Every POST carries the
 * incident version shown on the page and ends in a redirect to the incident (PRG).
 */
@Controller
@RequestMapping("/incidents/{incidentId}/corrective-actions")
public class CorrectiveActionController {

	static final String FORM_VIEW = "correctiveaction/form";

	static final String FORM = "correctiveActionForm";

	/** Default due date of a new action: one week from today. */
	static final int DEFAULT_DUE_DAYS = 7;

	private final CorrectiveActionService actionService;

	private final Clock clock;

	public CorrectiveActionController(CorrectiveActionService actionService, Clock clock) {
		this.actionService = actionService;
		this.clock = clock;
	}

	@GetMapping("/new")
	public String newForm(@PathVariable Long incidentId, Model model) {
		IncidentHeader incident = actionService.incidentForActions(incidentId);
		model.addAttribute(FORM, CorrectiveActionForm.blank(incident.version(), LocalDate.now(clock).plusDays(DEFAULT_DUE_DAYS)));
		return form(model, incident, null);
	}

	@PostMapping
	public String create(@PathVariable Long incidentId, @Validated @ModelAttribute(FORM) CorrectiveActionForm form,
			BindingResult result, Model model, RedirectAttributes redirect) {
		IncidentHeader incident = actionService.incidentForActions(incidentId);
		if (!result.hasErrors()) {
			try {
				actionService.create(incidentId, form.getIncidentVersion(), form.toCommand());
				Flash.success(redirect, "correctiveAction.flash.created");
				return "redirect:/incidents/" + incidentId;
			}
			catch (InvalidFieldException e) {
				result.rejectValue(e.getField(), e.getMessageKey());
			}
		}
		return form(model, incident, null);
	}

	@GetMapping("/{actionId}/edit")
	public String editForm(@PathVariable Long incidentId, @PathVariable Long actionId, Model model) {
		IncidentHeader incident = actionService.incidentForActions(incidentId);
		model.addAttribute(FORM, CorrectiveActionForm.from(actionService.get(incidentId, actionId), incident.version()));
		return form(model, incident, actionId);
	}

	@PostMapping("/{actionId}")
	public String update(@PathVariable Long incidentId, @PathVariable Long actionId,
			@Validated @ModelAttribute(FORM) CorrectiveActionForm form, BindingResult result, Model model,
			RedirectAttributes redirect) {
		IncidentHeader incident = actionService.incidentForActions(incidentId);
		if (!result.hasErrors()) {
			try {
				actionService.update(incidentId, actionId, form.getIncidentVersion(), form.toCommand());
				Flash.success(redirect, "correctiveAction.flash.updated");
				return "redirect:/incidents/" + incidentId;
			}
			catch (InvalidFieldException e) {
				result.rejectValue(e.getField(), e.getMessageKey());
			}
		}
		return form(model, incident, actionId);
	}

	/** Mark as done: a supervisor or the action's assignee. */
	@PostMapping("/{actionId}/done")
	public String markDone(@PathVariable Long incidentId, @PathVariable Long actionId, @RequestParam Long version,
			RedirectAttributes redirect) {
		actionService.markDone(incidentId, actionId, version);
		Flash.success(redirect, "correctiveAction.flash.done");
		return "redirect:/incidents/" + incidentId;
	}

	@PostMapping("/{actionId}/delete")
	public String delete(@PathVariable Long incidentId, @PathVariable Long actionId, @RequestParam Long version,
			RedirectAttributes redirect) {
		actionService.delete(incidentId, actionId, version);
		Flash.success(redirect, "correctiveAction.flash.deleted");
		return "redirect:/incidents/" + incidentId;
	}

	private String form(Model model, IncidentHeader incident, Long actionId) {
		model.addAttribute("incident", incident);
		model.addAttribute("actionId", actionId);
		model.addAttribute("formAction", actionId == null ? "/incidents/" + incident.id() + "/corrective-actions"
				: "/incidents/" + incident.id() + "/corrective-actions/" + actionId);
		model.addAttribute("assignees", actionService.assigneeOptions());
		model.addAttribute("contextTitle", incident.siteName());
		return FORM_VIEW;
	}

}
