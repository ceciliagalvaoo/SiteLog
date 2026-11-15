package hr.algebra.sitelog.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class LanguageLinkAdviceTests {

	private final LanguageLinkAdvice advice = new LanguageLinkAdvice();

	@Test
	void keepsTheOtherParametersAndReplacesOnlyLang() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sites/3");
		request.setQueryString("tab=logs&page=2&lang=en");

		assertThat(advice.langUrls(request))
			.containsEntry("hr", "?tab=logs&page=2&lang=hr")
			.containsEntry("en", "?tab=logs&page=2&lang=en");
	}

	@Test
	void withoutQueryString_onlySetsLang() {
		assertThat(LanguageLinkAdvice.switchTo(null, "hr")).isEqualTo("?lang=hr");
	}

	@Test
	void keepsEncodedValuesAsSent() {
		assertThat(LanguageLinkAdvice.switchTo("q=tower%20a&status=finished", "hr"))
			.isEqualTo("?q=tower%20a&status=finished&lang=hr");
	}

}
