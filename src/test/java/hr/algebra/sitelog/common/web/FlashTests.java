package hr.algebra.sitelog.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class FlashTests {

	private static Map<String, Object> attrs(RedirectAttributesModelMap redirect) {
		return new HashMap<>(redirect.getFlashAttributes());
	}

	@Test
	void success_withTitleDetailAndArgs_storesAllThree() {
		RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

		Flash.successWithDetail(redirect, "dailyLog.flash.saved", "dailyLog.flash.saved.detail", "Sunday");

		assertThat(attrs(redirect)).containsEntry(Flash.SUCCESS, "dailyLog.flash.saved")
			.containsEntry(Flash.DETAIL, "dailyLog.flash.saved.detail")
			.containsKey(Flash.ARGS);
		assertThat((Object[]) attrs(redirect).get(Flash.ARGS)).containsExactly("Sunday");
	}

	@Test
	void error_withoutArgs_storesOnlyTheTitleKey() {
		RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

		Flash.error(redirect, "crew.flash.deleteBlocked");

		assertThat(attrs(redirect)).containsOnlyKeys(Flash.ERROR);
	}

	@Test
	void success_withANonStringArgument_storesItAsMessageArgument() {
		RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

		Flash.success(redirect, "x.created", 42);

		assertThat(attrs(redirect)).doesNotContainKey(Flash.DETAIL);
		assertThat((Object[]) attrs(redirect).get(Flash.ARGS)).containsExactly(42);
	}

	/**
	 * Regression: a single {@code String} argument (site / crew name) must fill {0} in the title,
	 * not be taken as a detail message key (it used to render "??Tower A_en??").
	 */
	@Test
	void success_withOneStringArgument_storesItAsMessageArgumentNotAsDetailKey() {
		RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

		Flash.success(redirect, "site.flash.created", "Tower A");

		assertThat(attrs(redirect)).doesNotContainKey(Flash.DETAIL);
		assertThat((Object[]) attrs(redirect).get(Flash.ARGS)).containsExactly("Tower A");
	}

}
