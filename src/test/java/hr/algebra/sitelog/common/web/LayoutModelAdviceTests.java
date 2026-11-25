package hr.algebra.sitelog.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import hr.algebra.sitelog.incident.service.IncidentStatsService;
import hr.algebra.sitelog.notification.service.NotificationService;
import hr.algebra.sitelog.user.service.CurrentUserProvider;

/** The layout attributes are skipped for REST handlers (JSON, no layout to render). */
class LayoutModelAdviceTests {

	@RestController
	static class Api {

		public String handle() {
			return "json";
		}

	}

	@Controller
	static class Screen {

		public String handle() {
			return "view";
		}

	}

	private static MockHttpServletRequest requestFor(Object controller) throws NoSuchMethodException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
				new HandlerMethod(controller, controller.getClass().getMethod("handle")));
		return request;
	}

	@Test
	void restHandler_rendersBody_screenHandlerDoesNot() throws Exception {
		assertThat(LayoutModelAdvice.rendersBody(requestFor(new Api()))).isTrue();
		assertThat(LayoutModelAdvice.rendersBody(requestFor(new Screen()))).isFalse();
		assertThat(LayoutModelAdvice.rendersBody(new MockHttpServletRequest())).isFalse();
	}

	@Test
	void restHandler_getsNoLayoutQueries() throws Exception {
		CurrentUserProvider users = mock(CurrentUserProvider.class);
		NotificationService notifications = mock(NotificationService.class);
		IncidentStatsService incidents = mock(IncidentStatsService.class);
		ExtendedModelMap model = new ExtendedModelMap();

		new LayoutModelAdvice(users, notifications, incidents).layout(model, requestFor(new Api()));

		assertThat(model).isEmpty();
		verifyNoInteractions(users, notifications, incidents);
	}

}
