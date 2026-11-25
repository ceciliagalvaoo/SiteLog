package hr.algebra.sitelog.dailylog.web;

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

import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.web.Flash;
import hr.algebra.sitelog.dailylog.service.DailyLogHistory;
import hr.algebra.sitelog.dailylog.service.DailyLogHistoryService;
import hr.algebra.sitelog.dailylog.service.DailyLogService;
import hr.algebra.sitelog.dailylog.service.LogReopenService;

/**
 * AR1 reopen screens: the "request reopen" modal of the site manager and the approve / reject
 * modals of the safety supervisor on the daily log detail, the supervisor's queue of pending
 * requests, and the log's revision history (Envers before/after + reopen requests + actions).
 *
 * <p>A modal with validation errors (empty reason) re-renders the log detail with that modal
 * open ({@code reopenModal} / {@code rejectModal} = true) instead of redirecting, so the errors
 * stay next to the field; every successful POST redirects with a flash (PRG).
 */
@Controller
public class LogReopenController {

	static final String QUEUE_VIEW = "dailylog/reopen-requests";

	static final String HISTORY_VIEW = "dailylog/history";

	private final LogReopenService reopenService;

	private final DailyLogService dailyLogService;

	private final DailyLogHistoryService historyService;

	public LogReopenController(LogReopenService reopenService, DailyLogService dailyLogService,
			DailyLogHistoryService historyService) {
		this.reopenService = reopenService;
		this.dailyLogService = dailyLogService;
		this.historyService = historyService;
	}

	@PostMapping("/daily-logs/{id}/reopen-requests")
	public String request(@PathVariable Long id,
			@Validated @ModelAttribute(DailyLogController.REOPEN_FORM) ReopenRequestForm form, BindingResult result,
			Model model, RedirectAttributes redirect) {
		if (!result.hasErrors()) {
			try {
				reopenService.request(id, form.getVersion(), form.getReason());
				Flash.success(redirect, "reopen.flash.requested");
				return "redirect:/daily-logs/" + id;
			}
			catch (InvalidFieldException e) {
				result.rejectValue(e.getField(), e.getMessageKey());
			}
		}
		model.addAttribute("reopenModal", true);
		return DailyLogController.detail(model, dailyLogService, reopenService, id);
	}

	@PostMapping("/daily-logs/{id}/reopen-requests/{requestId}/approve")
	public String approve(@PathVariable Long id, @PathVariable Long requestId,
			@RequestParam(name = "note", required = false) String note, RedirectAttributes redirect) {
		reopenService.approve(id, requestId, note);
		Flash.success(redirect, "reopen.flash.approved");
		return "redirect:/daily-logs/" + id;
	}

	@PostMapping("/daily-logs/{id}/reopen-requests/{requestId}/reject")
	public String reject(@PathVariable Long id, @PathVariable Long requestId,
			@Validated @ModelAttribute(DailyLogController.REJECT_FORM) ReopenRejectForm form, BindingResult result,
			Model model, RedirectAttributes redirect) {
		if (!result.hasErrors()) {
			try {
				reopenService.reject(id, requestId, form.getReason());
				Flash.success(redirect, "reopen.flash.rejected");
				return "redirect:/daily-logs/" + id;
			}
			catch (InvalidFieldException e) {
				result.rejectValue(e.getField(), e.getMessageKey());
			}
		}
		model.addAttribute("rejectModal", true);
		return DailyLogController.detail(model, dailyLogService, reopenService, id);
	}

	/** Pending reopen requests on all sites, oldest first (the supervisor's work queue). */
	@GetMapping("/reopen-requests")
	public String queue(Model model) {
		model.addAttribute("requests", reopenService.pendingRequests());
		return QUEUE_VIEW;
	}

	/** Revision history of a log: who changed what, before and after, and why it was reopened. */
	@GetMapping("/daily-logs/{id}/history")
	public String history(@PathVariable Long id, Model model) {
		DailyLogHistory history = historyService.history(id);
		model.addAttribute("history", history);
		model.addAttribute("contextTitle", history.log().siteName());
		return HISTORY_VIEW;
	}

}
