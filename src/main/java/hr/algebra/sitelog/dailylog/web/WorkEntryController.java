package hr.algebra.sitelog.dailylog.web;

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
import hr.algebra.sitelog.dailylog.service.DailyLogHeader;
import hr.algebra.sitelog.dailylog.service.DailyLogService;
import hr.algebra.sitelog.dailylog.service.WorkEntryService;

/**
 * One work entry of a log (with its crew rows): add, edit, delete outside the full log form.
 * {@code logVersion} is the log version shown on the page; it travels as a hidden field and is
 * kept on re-render, so a conflict is still detected after a validation error.
 */
@Controller
@RequestMapping("/daily-logs/{logId}/work-entries")
public class WorkEntryController {

	static final String FORM_VIEW = "workentry/form";

	static final String FORM = "workEntryForm";

	private final WorkEntryService workEntryService;

	private final DailyLogService dailyLogService;

	private final CrewService crewService;

	public WorkEntryController(WorkEntryService workEntryService, DailyLogService dailyLogService,
			CrewService crewService) {
		this.workEntryService = workEntryService;
		this.dailyLogService = dailyLogService;
		this.crewService = crewService;
	}

	@GetMapping("/new")
	public String newForm(@PathVariable Long logId, Model model) {
		DailyLogHeader log = dailyLogService.getHeaderForChange(logId);
		model.addAttribute(FORM, WorkEntryForm.blank());
		return form(model, log, log.version(), null);
	}

	@PostMapping
	public String create(@PathVariable Long logId, @RequestParam Long logVersion,
			@Validated @ModelAttribute(FORM) WorkEntryForm form, BindingResult result,
			@RequestParam(name = "action", required = false) String action, Model model, RedirectAttributes redirect) {
		form.compact();
		DailyLogHeader log = dailyLogService.getHeaderForChange(logId);
		if (!RowActions.isSave(action)) {
			return rowAction(form, action, model, log, logVersion, null);
		}
		if (!result.hasErrors()) {
			try {
				workEntryService.create(logId, logVersion, form.toCommand());
				Flash.success(redirect, "workEntry.flash.created");
				return "redirect:/daily-logs/" + logId;
			}
			catch (InvalidFieldException e) {
				result.rejectValue(e.getField(), e.getMessageKey());
			}
		}
		return form(model, log, logVersion, null);
	}

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Long logId, @PathVariable Long id, Model model) {
		DailyLogHeader log = dailyLogService.getHeaderForChange(logId);
		model.addAttribute(FORM, WorkEntryForm.from(workEntryService.get(logId, id)));
		return form(model, log, log.version(), id);
	}

	@PostMapping("/{id}")
	public String update(@PathVariable Long logId, @PathVariable Long id, @RequestParam Long logVersion,
			@Validated @ModelAttribute(FORM) WorkEntryForm form, BindingResult result,
			@RequestParam(name = "action", required = false) String action, Model model, RedirectAttributes redirect) {
		form.compact();
		DailyLogHeader log = dailyLogService.getHeaderForChange(logId);
		if (!RowActions.isSave(action)) {
			return rowAction(form, action, model, log, logVersion, id);
		}
		if (!result.hasErrors()) {
			try {
				workEntryService.update(logId, id, logVersion, form.toCommand());
				Flash.success(redirect, "workEntry.flash.updated");
				return "redirect:/daily-logs/" + logId;
			}
			catch (InvalidFieldException e) {
				result.rejectValue(e.getField(), e.getMessageKey());
			}
		}
		return form(model, log, logVersion, id);
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long logId, @PathVariable Long id, @RequestParam Long logVersion,
			RedirectAttributes redirect) {
		workEntryService.delete(logId, id, logVersion);
		Flash.success(redirect, "workEntry.flash.deleted");
		return "redirect:/daily-logs/" + logId;
	}

	private String rowAction(WorkEntryForm form, String action, Model model, DailyLogHeader log, Long logVersion,
			Long entryId) {
		form.applyRowAction(action);
		model.asMap().remove(BindingResult.MODEL_KEY_PREFIX + FORM);
		model.addAttribute(FORM, form);
		return form(model, log, logVersion, entryId);
	}

	private String form(Model model, DailyLogHeader log, Long logVersion, Long entryId) {
		WorkEntryForm form = (WorkEntryForm) model.getAttribute(FORM);
		model.addAttribute("log", log);
		model.addAttribute("logVersion", logVersion);
		model.addAttribute("entryId", entryId);
		model.addAttribute("formAction", "/daily-logs/" + log.id() + "/work-entries" + (entryId == null ? "" : "/" + entryId));
		model.addAttribute("crews", crewService.optionsIncluding(form == null ? List.of() : form.selectedCrewIds()));
		model.addAttribute("contextTitle", log.siteName());
		return FORM_VIEW;
	}

}
