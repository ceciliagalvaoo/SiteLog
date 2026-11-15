package hr.algebra.sitelog.notification.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;

import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.notification.domain.Notification;
import hr.algebra.sitelog.notification.domain.NotificationType;
import hr.algebra.sitelog.notification.service.NotificationView;
import hr.algebra.sitelog.support.TestEntities;
import hr.algebra.sitelog.support.WebSliceConfig;
import hr.algebra.sitelog.support.WebSliceTest;

/** NotificationService is already a {@code @MockitoBean} of the base class (the layout needs it). */
@WebMvcTest(NotificationController.class)
@Import(WebSliceConfig.class)
@WithMockUser
class NotificationControllerTests extends WebSliceTest {

	@Test
	void list_rendersTheUsersNotifications() throws Exception {
		when(notificationService.list(any())).thenReturn(new PageImpl<>(List.of(new NotificationView(1L,
				NotificationType.DAILY_LOG_UNSIGNED, "Unsigned log", "ZG-01 has no signed log", "/daily-logs/10",
				ZonedDateTime.ofInstant(TestEntities.NOW, TestEntities.ZONE), false))));

		mvc.perform(get("/notifications"))
			.andExpect(status().isOk())
			.andExpect(view().name(NotificationController.LIST_VIEW))
			.andExpect(content().string(containsString("ZG-01 has no signed log")));
	}

	@Test
	void open_withLocalLink_marksReadAndGoesToTheRecord() throws Exception {
		when(notificationService.markRead(1L)).thenReturn("/daily-logs/10");

		mvc.perform(post("/notifications/1/open").with(csrf())).andExpect(redirectedUrl("/daily-logs/10"));
		verify(notificationService).markRead(1L);
	}

	@Test
	void open_withExternalLink_isNotAnOpenRedirect() throws Exception {
		when(notificationService.markRead(1L)).thenReturn("//evil.example/x");

		mvc.perform(post("/notifications/1/open").with(csrf())).andExpect(redirectedUrl("/notifications"));
	}

	@Test
	void markRead_returnsToTheSameHostReferer() throws Exception {
		mvc.perform(post("/notifications/1/read").with(csrf()).header("Referer", "http://localhost/sites/3?tab=logs"))
			.andExpect(redirectedUrl("/sites/3?tab=logs"));
		verify(notificationService).markRead(1L);
	}

	@Test
	void markRead_ofSomeoneElsesNotification_renders404() throws Exception {
		when(notificationService.markRead(5L)).thenThrow(new NotFoundException(Notification.class, 5L));

		mvc.perform(post("/notifications/5/read").with(csrf()))
			.andExpect(status().isNotFound())
			.andExpect(view().name("error/404"));
	}

	@Test
	void readAll_marksAllAndFallsBackToTheListWithoutReferer() throws Exception {
		mvc.perform(post("/notifications/read-all").with(csrf())).andExpect(redirectedUrl("/notifications"));
		verify(notificationService).markAllRead();
	}

}
