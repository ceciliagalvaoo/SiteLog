package hr.algebra.sitelog.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import hr.algebra.sitelog.incident.service.IncidentStatsService;
import hr.algebra.sitelog.notification.service.NotificationService;
import hr.algebra.sitelog.user.service.CurrentUserProvider;

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
 */
@ControllerAdvice(annotations = Controller.class)
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
	public void layout(Model model) {
		model.addAttribute("currentUser", currentUserProvider.currentUser());
		model.addAttribute("unreadCount", notificationService.unreadCount());
		model.addAttribute("notifications", notificationService.latestUnread());
		model.addAttribute("openIncidentCount", incidentStats.openIncidentCount());
	}

}
