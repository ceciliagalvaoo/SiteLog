package hr.algebra.sitelog.site.web;

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

import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.web.Flash;
import hr.algebra.sitelog.common.web.Pages;
import hr.algebra.sitelog.dailylog.service.DailyLogService;
import hr.algebra.sitelog.site.service.SiteDetails;
import hr.algebra.sitelog.site.service.SiteService;

/**
 * Site screens: list with filter, detail (overview KPIs and the daily logs tab), New site /
 * Site settings form and delete. Thin: form -> service -> redirect with flash (PRG).
 */
@Controller
@RequestMapping("/sites")
public class SiteController {

	static final String LIST_VIEW = "site/list";

	static final String DETAIL_VIEW = "site/detail";

	static final String FORM_VIEW = "site/form";

	/** Tabs of the site detail (prototype pp. 29-33). */
	static final Set<String> TABS = Set.of("overview", "logs", "incidents", "compliance");

	private static final Set<String> SITE_SORT = Set.of("code", "name", "startDate");

	private static final Set<String> LOG_SORT = Set.of("logDate", "status");

	private final SiteService siteService;

	private final DailyLogService dailyLogService;

	public SiteController(SiteService siteService, DailyLogService dailyLogService) {
		this.siteService = siteService;
		this.dailyLogService = dailyLogService;
	}

	@GetMapping
	public String list(@RequestParam(name = "q", required = false) String filter,
			@PageableDefault(size = 20) Pageable pageable, Model model) {
		model.addAttribute("sites", siteService.list(filter,
				Pages.restrict(pageable, SITE_SORT, Sort.by("name"))));
		model.addAttribute("q", filter);
		return LIST_VIEW;
	}

	@GetMapping("/{id}")
	public String detail(@PathVariable Long id, @RequestParam(name = "tab", defaultValue = "overview") String tab,
			@PageableDefault(size = 20) Pageable pageable, Model model) {
		SiteDetails site = siteService.get(id);
		model.addAttribute("site", site);
		model.addAttribute("tab", TABS.contains(tab) ? tab : "overview");
		model.addAttribute("logs", dailyLogService.listForSite(id,
				Pages.restrict(pageable, LOG_SORT, Sort.by(Sort.Direction.DESC, "logDate"))));
		model.addAttribute("contextTitle", site.name());
		return DETAIL_VIEW;
	}

	@GetMapping("/new")
	public String newForm(Model model) {
		model.addAttribute("siteForm", new SiteForm());
		return form(model, null);
	}

	@PostMapping
	public String create(@Validated @ModelAttribute("siteForm") SiteForm form, BindingResult result, Model model,
			RedirectAttributes redirect) {
		if (!result.hasErrors()) {
			try {
				Long id = siteService.create(form.toCommand());
				Flash.success(redirect, "site.flash.created", form.getName());
				return "redirect:/sites/" + id;
			}
			catch (InvalidFieldException e) {
				result.rejectValue(e.getField(), e.getMessageKey());
			}
		}
		return form(model, null);
	}

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Long id, Model model) {
		model.addAttribute("siteForm", SiteForm.from(siteService.get(id)));
		return form(model, id);
	}

	@PostMapping("/{id}")
	public String update(@PathVariable Long id, @Validated @ModelAttribute("siteForm") SiteForm form,
			BindingResult result, Model model, RedirectAttributes redirect) {
		if (!result.hasErrors()) {
			try {
				siteService.update(id, form.toCommand());
				Flash.success(redirect, "site.flash.updated", form.getName());
				return "redirect:/sites/" + id;
			}
			catch (InvalidFieldException e) {
				result.rejectValue(e.getField(), e.getMessageKey());
			}
		}
		return form(model, id);
	}

	/** Delete (confirmed in a modal). A site with records stays; the user is told to set an end date. */
	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, RedirectAttributes redirect) {
		try {
			siteService.delete(id);
			Flash.success(redirect, "site.flash.deleted");
			return "redirect:/sites";
		}
		catch (DeletionNotAllowedException e) {
			Flash.error(redirect, e.getMessageKey());
			return "redirect:/sites/" + id;
		}
	}

	private String form(Model model, Long siteId) {
		model.addAttribute("siteId", siteId);
		model.addAttribute("managers", siteService.managerOptions());
		return FORM_VIEW;
	}

}
