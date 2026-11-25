package hr.algebra.sitelog.incident.web;

import java.time.Clock;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.SmartValidator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.MissingSeverityFieldsException;
import hr.algebra.sitelog.common.web.Flash;
import hr.algebra.sitelog.common.web.Pages;
import hr.algebra.sitelog.crew.service.CrewService;
import hr.algebra.sitelog.incident.domain.IncidentCategory;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.domain.Severity;
import hr.algebra.sitelog.incident.service.DailyLogOption;
import hr.algebra.sitelog.incident.service.IncidentDetails;
import hr.algebra.sitelog.incident.service.IncidentFilter;
import hr.algebra.sitelog.incident.service.IncidentService;
import hr.algebra.sitelog.site.service.SiteListItem;
import hr.algebra.sitelog.site.service.SiteService;

/**
 * Incident screens (AR2): list (all sites and per site, with status / severity filters), report
 * form, detail with the workflow buttons the caller's role allows and the timeline, edit form,
 * and the workflow POSTs (start review, sign-off, close, delete). Every POST ends in a redirect
 * with a flash (PRG); the rules themselves are in {@link IncidentService}.
 *
 * <p>Validation: the default constraints of {@link IncidentForm} always; the {@link HighSeverity}
 * group (root cause, witnesses, medical treatment, authority notified) only for HIGH / CRITICAL.
 */
@Controller
public class IncidentController {

	static final String LIST_VIEW = "incident/list";

	static final String FORM_VIEW = "incident/form";

	static final String DETAIL_VIEW = "incident/detail";

	static final String FORM = "incidentForm";

	static final Set<String> SORTABLE = Set.of("occurredAt", "severity", "status", "title");

	static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "occurredAt");

	private final IncidentService incidentService;

	private final SiteService siteService;

	private final CrewService crewService;

	private final SmartValidator validator;

	private final Clock clock;

	public IncidentController(IncidentService incidentService, SiteService siteService, CrewService crewService,
			SmartValidator validator, Clock clock) {
		this.incidentService = incidentService;
		this.siteService = siteService;
		this.crewService = crewService;
		this.validator = validator;
		this.clock = clock;
	}

	// ------------------------------------------------------------------ lists

	@GetMapping("/incidents")
	public String list(@RequestParam(required = false) IncidentStatus status,
			@RequestParam(required = false) Severity severity, @PageableDefault(size = 20) Pageable pageable,
			Model model) {
		return list(null, status, severity, pageable, model);
	}

	@GetMapping("/sites/{siteId}/incidents")
	public String listForSite(@PathVariable Long siteId, @RequestParam(required = false) IncidentStatus status,
			@RequestParam(required = false) Severity severity, @PageableDefault(size = 20) Pageable pageable,
			Model model) {
		SiteListItem site = siteService.getSummary(siteId);
		model.addAttribute("site", site);
		model.addAttribute("contextTitle", site.name());
		return list(siteId, status, severity, pageable, model);
	}

	private String list(Long siteId, IncidentStatus status, Severity severity, Pageable pageable, Model model) {
		model.addAttribute("page", incidentService.list(new IncidentFilter(siteId, status, severity),
				Pages.restrict(pageable, SORTABLE, DEFAULT_SORT)));
		model.addAttribute("status", status);
		model.addAttribute("severity", severity);
		model.addAttribute("statuses", IncidentStatus.values());
		model.addAttribute("severities", Severity.values());
		model.addAttribute("listUrl", siteId == null ? "/incidents" : "/sites/" + siteId + "/incidents");
		return LIST_VIEW;
	}

	// ------------------------------------------------------------------ report (create)

	@GetMapping("/sites/{siteId}/incidents/new")
	public String newForm(@PathVariable Long siteId, Model model) {
		List<DailyLogOption> logs = incidentService.dailyLogOptionsForReport(siteId);
		model.addAttribute(FORM, IncidentForm.blank(clock));
		return form(model, siteService.getSummary(siteId), logs, null);
	}

	@PostMapping("/sites/{siteId}/incidents")
	public String create(@PathVariable Long siteId, @ModelAttribute(FORM) IncidentForm form, BindingResult result,
			Model model, RedirectAttributes redirect) {
		// protected read first: a refused caller gets 403 before any validation detail
		List<DailyLogOption> logs = incidentService.dailyLogOptionsForReport(siteId);
		validate(form, result);
		if (!result.hasErrors()) {
			try {
				Long id = incidentService.report(siteId, form.toCommand(clock.getZone()));
				Flash.success(redirect, form.requiresExtendedFields() ? "incident.flash.reported.high"
						: "incident.flash.reported", form.getTitle());
				return "redirect:/incidents/" + id;
			}
			catch (InvalidFieldException e) {
				result.rejectValue(e.getField(), e.getMessageKey());
			}
			catch (MissingSeverityFieldsException e) {
				rejectMissing(result, e);
			}
		}
		return form(model, siteService.getSummary(siteId), logs, null);
	}

	// ------------------------------------------------------------------ detail and edit

	@GetMapping("/incidents/{id}")
	public String detail(@PathVariable Long id, Model model) {
		IncidentDetails incident = incidentService.getDetail(id);
		model.addAttribute("incident", incident);
		model.addAttribute("contextTitle", incident.siteName());
		return DETAIL_VIEW;
	}

	@GetMapping("/incidents/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		IncidentDetails incident = incidentService.getForEdit(id);
		model.addAttribute(FORM, IncidentForm.from(incident));
		return editForm(model, incident);
	}

	@PostMapping("/incidents/{id}")
	public String update(@PathVariable Long id, @ModelAttribute(FORM) IncidentForm form, BindingResult result,
			Model model, RedirectAttributes redirect) {
		IncidentDetails incident = incidentService.getForEdit(id);
		validate(form, result);
		if (!result.hasErrors()) {
			try {
				incidentService.update(id, form.getVersion(), form.toCommand(clock.getZone()));
				Flash.success(redirect, "incident.flash.updated", form.getTitle());
				return "redirect:/incidents/" + id;
			}
			catch (InvalidFieldException e) {
				result.rejectValue(e.getField(), e.getMessageKey());
			}
			catch (MissingSeverityFieldsException e) {
				rejectMissing(result, e);
			}
		}
		return editForm(model, incident);
	}

	// ------------------------------------------------------------------ workflow (version = the one on the page)

	@PostMapping("/incidents/{id}/start-review")
	public String startReview(@PathVariable Long id, @RequestParam Long version, RedirectAttributes redirect) {
		incidentService.startReview(id, version);
		Flash.success(redirect, "incident.flash.reviewStarted");
		return "redirect:/incidents/" + id;
	}

	@PostMapping("/incidents/{id}/sign-off")
	public String signOff(@PathVariable Long id, @RequestParam Long version, RedirectAttributes redirect) {
		incidentService.signOff(id, version);
		Flash.success(redirect, "incident.flash.signedOff");
		return "redirect:/incidents/" + id;
	}

	@PostMapping("/incidents/{id}/close")
	public String close(@PathVariable Long id, @RequestParam Long version, RedirectAttributes redirect) {
		incidentService.close(id, version);
		Flash.success(redirect, "incident.flash.closed");
		return "redirect:/incidents/" + id;
	}

	@PostMapping("/incidents/{id}/delete")
	public String delete(@PathVariable Long id, @RequestParam Long version, RedirectAttributes redirect) {
		try {
			Long siteId = incidentService.delete(id, version);
			Flash.success(redirect, "incident.flash.deleted");
			return "redirect:/sites/" + siteId + "/incidents";
		}
		catch (DeletionNotAllowedException e) {
			Flash.error(redirect, e.getMessageKey());
			return "redirect:/incidents/" + id;
		}
	}

	// ------------------------------------------------------------------ helpers

	/** Default constraints always, the {@link HighSeverity} group only for HIGH / CRITICAL. */
	private void validate(IncidentForm form, BindingResult result) {
		validator.validate(form, result);
		if (form.requiresExtendedFields()) {
			validator.validate(form, result, HighSeverity.class);
		}
	}

	/** The service's AR2 check, shown on the same fields as the form validation would. */
	private static void rejectMissing(BindingResult result, MissingSeverityFieldsException e) {
		e.getFields().forEach(field -> result.rejectValue(field, "validation.incident." + field + ".required"));
	}

	private String editForm(Model model, IncidentDetails incident) {
		model.addAttribute("incident", incident);
		return form(model, siteService.getSummary(incident.siteId()), incidentService.dailyLogOptions(incident.siteId()),
				incident.id());
	}

	private String form(Model model, SiteListItem site, List<DailyLogOption> logs, Long incidentId) {
		IncidentForm form = (IncidentForm) model.getAttribute(FORM);
		model.addAttribute("site", site);
		model.addAttribute("incidentId", incidentId);
		model.addAttribute("formAction", incidentId == null ? "/sites/" + site.id() + "/incidents" : "/incidents/" + incidentId);
		model.addAttribute("dailyLogs", logs);
		model.addAttribute("crews", crewService.optionsIncluding(form == null ? List.of() : form.getAffectedCrewIds()));
		model.addAttribute("severities", Severity.values());
		model.addAttribute("categories", IncidentCategory.values());
		model.addAttribute("contextTitle", site.name());
		return FORM_VIEW;
	}

}
