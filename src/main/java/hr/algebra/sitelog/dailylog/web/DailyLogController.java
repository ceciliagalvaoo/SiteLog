package hr.algebra.sitelog.dailylog.web;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.web.Flash;
import hr.algebra.sitelog.crew.service.CrewService;
import hr.algebra.sitelog.dailylog.domain.Weather;
import hr.algebra.sitelog.dailylog.service.DailyLogDetails;
import hr.algebra.sitelog.dailylog.service.DailyLogService;
import hr.algebra.sitelog.site.service.SiteListItem;
import hr.algebra.sitelog.site.service.SiteService;

/**
 * Daily log screens: the nested form (log + work entries + crew rows, as in the prototype),
 * the detail (read-only when signed off), sign-off and delete.
 *
 * <p>The form's {@code action} button either saves ({@code save}) or changes the rows
 * ({@code addEntry}, {@code removeEntry-i}, {@code addCrew-i}, {@code removeCrew-i-j}); a row
 * change re-renders the form without saving and without showing validation errors yet.
 */
@Controller
public class DailyLogController {

	static final String FORM_VIEW = "dailylog/form";

	static final String DETAIL_VIEW = "dailylog/detail";

	static final String FORM = "dailyLogForm";

	private final DailyLogService dailyLogService;

	private final SiteService siteService;

	private final CrewService crewService;

	private final Clock clock;

	public DailyLogController(DailyLogService dailyLogService, SiteService siteService, CrewService crewService,
			Clock clock) {
		this.dailyLogService = dailyLogService;
		this.siteService = siteService;
		this.crewService = crewService;
		this.clock = clock;
	}

	@GetMapping("/daily-logs/{id}")
	public String detail(@PathVariable Long id, Model model) {
		DailyLogDetails log = dailyLogService.getDetail(id);
		model.addAttribute("log", log);
		model.addAttribute("contextTitle", log.siteName());
		return DETAIL_VIEW;
	}

	@GetMapping("/sites/{siteId}/daily-logs/new")
	public String newForm(@PathVariable Long siteId, Model model) {
		model.addAttribute(FORM, DailyLogForm.blank(LocalDate.now(clock)));
		return form(model, siteService.getSummary(siteId), null);
	}

	@PostMapping("/sites/{siteId}/daily-logs")
	public String create(@PathVariable Long siteId, @Validated @ModelAttribute(FORM) DailyLogForm form,
			BindingResult result, @RequestParam(name = "action", required = false) String action, Model model,
			RedirectAttributes redirect, Locale locale) {
		form.compact();
		SiteListItem site = siteService.getSummary(siteId);
		if (!RowActions.isSave(action)) {
			return rowAction(form, action, model, site, null);
		}
		if (!result.hasErrors()) {
			try {
				dailyLogService.create(siteId, form.toCommand());
				Flash.success(redirect, "dailyLog.flash.saved", "dailyLog.flash.saved.detail",
						longDate(form.getLogDate(), locale));
				return "redirect:/sites/" + siteId;
			}
			catch (InvalidFieldException e) {
				result.rejectValue(e.getField(), e.getMessageKey());
			}
		}
		return form(model, site, null);
	}

	@GetMapping("/daily-logs/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		DailyLogDetails log = dailyLogService.getForEdit(id);
		model.addAttribute(FORM, DailyLogForm.from(log));
		model.addAttribute("log", log);
		return form(model, siteService.getSummary(log.siteId()), id);
	}

	@PostMapping("/daily-logs/{id}")
	public String update(@PathVariable Long id, @Validated @ModelAttribute(FORM) DailyLogForm form,
			BindingResult result, @RequestParam(name = "action", required = false) String action, Model model,
			RedirectAttributes redirect, Locale locale) {
		form.compact();
		DailyLogDetails log = dailyLogService.getForEdit(id);
		model.addAttribute("log", log);
		SiteListItem site = siteService.getSummary(log.siteId());
		if (!RowActions.isSave(action)) {
			return rowAction(form, action, model, site, id);
		}
		if (!result.hasErrors()) {
			try {
				dailyLogService.update(id, form.getVersion(), form.toCommand());
				Flash.success(redirect, "dailyLog.flash.updated", longDate(form.getLogDate(), locale));
				return "redirect:/daily-logs/" + id;
			}
			catch (InvalidFieldException e) {
				result.rejectValue(e.getField(), e.getMessageKey());
			}
		}
		return form(model, site, id);
	}

	/** Sign-off (AR1): from now on the log is locked; {@code version} is the one shown on the page. */
	@PostMapping("/daily-logs/{id}/sign-off")
	public String signOff(@PathVariable Long id, @RequestParam Long version, RedirectAttributes redirect) {
		dailyLogService.signOff(id, version);
		Flash.success(redirect, "dailyLog.flash.signedOff");
		return "redirect:/daily-logs/" + id;
	}

	/** Delete a DRAFT log (confirmed in a modal) with its work entries and crew rows. */
	@PostMapping("/daily-logs/{id}/delete")
	public String delete(@PathVariable Long id, @RequestParam Long version, RedirectAttributes redirect) {
		try {
			Long siteId = dailyLogService.delete(id, version);
			Flash.success(redirect, "dailyLog.flash.deleted");
			return "redirect:/sites/" + siteId + "?tab=logs";
		}
		catch (DeletionNotAllowedException e) {
			Flash.error(redirect, e.getMessageKey());
			return "redirect:/daily-logs/" + id;
		}
	}

	private String rowAction(DailyLogForm form, String action, Model model, SiteListItem site, Long logId) {
		form.applyRowAction(action);
		// nothing was saved: drop the errors of this half-filled form (indexes may have shifted, too)
		model.asMap().remove(BindingResult.MODEL_KEY_PREFIX + FORM);
		model.addAttribute(FORM, form);
		return form(model, site, logId);
	}

	private String form(Model model, SiteListItem site, Long logId) {
		DailyLogForm form = (DailyLogForm) model.getAttribute(FORM);
		model.addAttribute("site", site);
		model.addAttribute("logId", logId);
		model.addAttribute("formAction", logId == null ? "/sites/" + site.id() + "/daily-logs" : "/daily-logs/" + logId);
		model.addAttribute("crews", crewService.optionsIncluding(form == null ? List.of() : form.selectedCrewIds()));
		model.addAttribute("weathers", Weather.values());
		model.addAttribute("contextTitle", site.name());
		return FORM_VIEW;
	}

	/** "Saturday, 7 November 2026" in the UI language, for the flash text. */
	static String longDate(LocalDate date, Locale locale) {
		return date == null ? "" : DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(date);
	}

}
