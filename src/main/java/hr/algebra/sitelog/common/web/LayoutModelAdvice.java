package hr.algebra.sitelog.common.web;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import hr.algebra.sitelog.incident.service.IncidentStatsService;
import hr.algebra.sitelog.notification.service.NotificationService;
import hr.algebra.sitelog.user.service.CurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Model attributes the layout of every screen needs:
 * <ul>
 * <li>{@code currentUser} - user menu (fullName, initials, role);</li>
 * <li>{@code unreadCount} - badge of the notification bell;</li>
 * <li>{@code notifications} - the newest 10 unread notifications for the bell dropdown;</li>
 * <li>{@code openIncidentCount} - badge of the "Incidents" sidebar link.</li>
 * </ul>
 * {@code contextTitle} (top bar) is page-specific and set by each controller.
 * Runs before every {@code @Controller} handler (a form re-rendered after a failed POST needs the
 * layout too). Cost: three small indexed queries on {@code notification (recipient_id, read_at)} and the
 * user lookup.
 *
 * <p>Limited to the application's own controllers: Spring Boot's {@code BasicErrorController}
 * (the 403 / 404 / 500 pages) must render even when the database is the reason for the error, so
 * the error pages never query it (the layout treats every attribute as optional).
 *
 * <p>An anonymous request (the login page) gets none of these attributes: there is no user to
 * show, and the notification and incident services require a signed-in caller.
 *
 * <p>The REST controllers ({@code @RestController}, i.e. {@code @ResponseBody}) are skipped too:
 * they render JSON, not the layout, so the four queries would be wasted on every API call.
 */
@ControllerAdvice(basePackages = "hr.algebra.sitelog")
public class LayoutModelAdvice {

	private final CurrentUserProvider currentUserProvider;

	private final NotificationService notificationService;

	private final IncidentStatsService incidentStats;

	public LayoutModelAdvice(CurrentUserProvider currentUserProvider, NotificationService notificationService,
			IncidentStatsService incidentStats) {
		this.currentUserProvider = currentUserProvider;
		this.notificationService = notificationService;
		this.incidentStats = incidentStats;
	}

	@ModelAttribute
	public void layout(Model model, HttpServletRequest request) {
		if (rendersBody(request)) {
			return;
		}
		currentUserProvider.findCurrentUser().ifPresent(user -> {
			model.addAttribute("currentUser", user);
			model.addAttribute("unreadCount", notificationService.unreadCount());
			model.addAttribute("notifications", notificationService.latestUnread());
			model.addAttribute("openIncidentCount", incidentStats.openIncidentCount());
		});
	}

	/** True for a handler whose return value is the response body (REST), set by the handler mapping. */
	static boolean rendersBody(HttpServletRequest request) {
		return request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod handler
				&& (AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), ResponseBody.class)
						|| handler.hasMethodAnnotation(ResponseBody.class));
	}

}
