package hr.algebra.sitelog.support;

import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import hr.algebra.sitelog.incident.service.IncidentStatsService;
import hr.algebra.sitelog.notification.service.NotificationService;
import hr.algebra.sitelog.user.domain.RoleName;
import hr.algebra.sitelog.user.service.CurrentUser;
import hr.algebra.sitelog.user.service.CurrentUserProvider;

/**
 * Base of the controller slice tests. {@code @WebMvcTest} also loads every
 * {@code @ControllerAdvice}: {@code WebExceptionHandler} (no dependencies) and
 * {@code LayoutModelAdvice}, whose three collaborators are mocked here and filled with a
 * signed-in site manager, so the real layout renders around every page.
 *
 * <p>Each subclass declares {@code @WebMvcTest(TheController.class)},
 * {@code @Import(WebSliceConfig.class)} and {@code @WithMockUser}, plus a {@code @MockitoBean}
 * per service its controller uses.
 */
public abstract class WebSliceTest {

	@Autowired
	protected MockMvc mvc;

	@MockitoBean
	protected CurrentUserProvider currentUserProvider;

	@MockitoBean
	protected NotificationService notificationService;

	@MockitoBean
	protected IncidentStatsService incidentStatsService;

	@BeforeEach
	void layoutModel() {
		when(currentUserProvider.currentUser())
			.thenReturn(new CurrentUser(7L, "manager@sitelog.test", "Marko Kovacevic", RoleName.SITE_MANAGER));
		when(currentUserProvider.currentUserId()).thenReturn(7L);
		when(notificationService.unreadCount()).thenReturn(0L);
		when(notificationService.latestUnread()).thenReturn(List.of());
		when(incidentStatsService.openIncidentCount()).thenReturn(0L);
	}

}
