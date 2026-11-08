package hr.algebra.sitelog.notification.web;

import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import hr.algebra.sitelog.common.web.Pages;
import hr.algebra.sitelog.common.web.SafeRedirects;
import hr.algebra.sitelog.notification.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Notifications page and the bell's "mark as read" actions (POST, then redirect back: PRG).
 * The bell itself is filled for every page by {@code LayoutModelAdvice}.
 */
@Controller
@RequestMapping("/notifications")
public class NotificationController {

	static final String LIST_VIEW = "notification/list";

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping
	public String list(@PageableDefault(size = 20) Pageable pageable, Model model) {
		model.addAttribute("page", notificationService.list(
				Pages.restrict(pageable, Set.of(), Sort.unsorted())));
		return LIST_VIEW;
	}

	/** Marks one as read and returns to the page the user was on. */
	@PostMapping("/{id}/read")
	public String markRead(@PathVariable Long id, HttpServletRequest request) {
		notificationService.markRead(id);
		return SafeRedirects.back(request, "/notifications");
	}

	/** Opens a notification: marks it read and goes to the record it points to. */
	@PostMapping("/{id}/open")
	public String open(@PathVariable Long id) {
		String link = notificationService.markRead(id);
		return "redirect:" + (SafeRedirects.isLocalPath(link) ? link : "/notifications");
	}

	@PostMapping("/read-all")
	public String markAllRead(HttpServletRequest request) {
		notificationService.markAllRead();
		return SafeRedirects.back(request, "/notifications");
	}

}
