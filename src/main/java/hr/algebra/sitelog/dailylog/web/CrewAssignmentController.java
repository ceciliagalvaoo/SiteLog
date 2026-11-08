package hr.algebra.sitelog.dailylog.web;

import java.util.Collections;
import java.util.List;

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
import hr.algebra.sitelog.crew.service.CrewService;
import hr.algebra.sitelog.dailylog.service.CrewAssignmentService;
import hr.algebra.sitelog.dailylog.service.DailyLogHeader;
import hr.algebra.sitelog.dailylog.service.DailyLogService;

/**
 * One crew row (CrewAssignment: crew, hours worked, workers, task) of a work entry: add, edit,
 * delete. Nested URLs keep the log id, so the service can check that the row belongs to it.
 */
@Controller
@RequestMapping("/daily-logs/{logId}")
public class CrewAssignmentController {

	static final String FORM_VIEW = "crewassignment/form";

	static final String FORM = "crewAssignmentForm";

	private final CrewAssignmentService crewAssignmentService;

	private final DailyLogService dailyLogService;

	private final CrewService crewService;

	public CrewAssignmentController(CrewAssignmentService crewAssignmentService, DailyLogService dailyLogService,
			CrewService crewService) {
		this.crewAssignmentService = crewAssignmentService;
		this.dailyLogService = dailyLogService;
		this.crewService = crewService;
	}

	@GetMapping("/work-entries/{entryId}/crew-assignments/new")
	public String newForm(@PathVariable Long logId, @PathVariable Long entryId, Model model) {
		DailyLogHeader log = dailyLogService.getHeaderForChange(logId);
		model.addAttribute(FORM, new CrewAssignmentForm());
		return form(model, log, log.version(), entryId, null);
	}

	@PostMapping("/work-entries/{entryId}/crew-assignments")
	public String create(@PathVariable Long logId, @PathVariable Long entryId, @RequestParam Long logVersion,
			@Validated @ModelAttribute(FORM) CrewAssignmentForm form, BindingResult result, Model model,
			RedirectAttributes redirect) {
		if (!result.hasErrors()) {
			try {
				crewAssignmentService.create(logId, entryId, logVersion, form.toCommand());
				Flash.success(redirect, "crewAssignment.flash.created");
				return "redirect:/daily-logs/" + logId;
			}
			catch (InvalidFieldException e) {
				result.rejectValue(e.getField(), e.getMessageKey());
			}
		}
		return form(model, dailyLogService.getHeaderForChange(logId), logVersion, entryId, null);
	}

	@GetMapping("/crew-assignments/{id}/edit")
	public String editForm(@PathVariable Long logId, @PathVariable Long id, Model model) {
		DailyLogHeader log = dailyLogService.getHeaderForChange(logId);
		model.addAttribute(FORM, CrewAssignmentForm.from(crewAssignmentService.get(logId, id)));
		return form(model, log, log.version(), crewAssignmentService.workEntryIdOf(logId, id), id);
	}

	@PostMapping("/crew-assignments/{id}")
	public String update(@PathVariable Long logId, @PathVariable Long id, @RequestParam Long logVersion,
			@Validated @ModelAttribute(FORM) CrewAssignmentForm form, BindingResult result, Model model,
			RedirectAttributes redirect) {
		if (!result.hasErrors()) {
			try {
				crewAssignmentService.update(logId, id, logVersion, form.toCommand());
				Flash.success(redirect, "crewAssignment.flash.updated");
				return "redirect:/daily-logs/" + logId;
			}
			catch (InvalidFieldException e) {
				result.rejectValue(e.getField(), e.getMessageKey());
			}
		}
		return form(model, dailyLogService.getHeaderForChange(logId), logVersion,
				crewAssignmentService.workEntryIdOf(logId, id), id);
	}

	@PostMapping("/crew-assignments/{id}/delete")
	public String delete(@PathVariable Long logId, @PathVariable Long id, @RequestParam Long logVersion,
			RedirectAttributes redirect) {
		crewAssignmentService.delete(logId, id, logVersion);
		Flash.success(redirect, "crewAssignment.flash.deleted");
		return "redirect:/daily-logs/" + logId;
	}

	private String form(Model model, DailyLogHeader log, Long logVersion, Long entryId, Long assignmentId) {
		CrewAssignmentForm form = (CrewAssignmentForm) model.getAttribute(FORM);
		List<Long> selected = form == null || form.getCrewId() == null ? Collections.emptyList()
				: List.of(form.getCrewId());
		model.addAttribute("log", log);
		model.addAttribute("logVersion", logVersion);
		model.addAttribute("workEntryId", entryId);
		model.addAttribute("assignmentId", assignmentId);
		model.addAttribute("formAction", assignmentId == null
				? "/daily-logs/" + log.id() + "/work-entries/" + entryId + "/crew-assignments"
				: "/daily-logs/" + log.id() + "/crew-assignments/" + assignmentId);
		model.addAttribute("crews", crewService.optionsIncluding(selected));
		model.addAttribute("contextTitle", log.siteName());
		return FORM_VIEW;
	}

}
