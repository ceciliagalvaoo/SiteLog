package hr.algebra.sitelog.dailylog.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/** History values are shown in the reader's format, never as raw ISO text or 8.00. */
class FieldChangeTests {

	private static final Locale HR = Locale.forLanguageTag("hr");

	private final FieldChange change = new FieldChange("history.field.logDate", null, null, null);

	private String en(Object value) {
		return change.display(value, "d MMM yyyy", "HH:mm", "d MMM yyyy, HH:mm", Locale.ENGLISH);
	}

	@Test
	void datesTimesAndNumbersFollowTheGivenPatternsAndLocale() {
		assertThat(en(LocalDate.of(2026, 10, 29))).isEqualTo("29 Oct 2026");
		assertThat(en(LocalTime.of(7, 0))).isEqualTo("07:00");
		assertThat(en(LocalDateTime.of(2026, 10, 29, 18, 2))).isEqualTo("29 Oct 2026, 18:02");
		assertThat(en(new BigDecimal("8.00"))).isEqualTo("8");
		assertThat(en(new BigDecimal("6.50"))).isEqualTo("6.5");
		assertThat(change.display(new BigDecimal("6.50"), "d. M. yyyy.", "HH:mm", "d. M. yyyy. HH:mm", HR))
			.isEqualTo("6,5");
		assertThat(change.display(LocalDate.of(2026, 10, 29), "d. M. yyyy.", "HH:mm", "d. M. yyyy. HH:mm", HR))
			.isEqualTo("29. 10. 2026.");
	}

	@Test
	void otherValuesAreShownAsTextAndNullStaysNull() {
		assertThat(en(3)).isEqualTo("3");
		assertThat(en("Formwork")).isEqualTo("Formwork");
		assertThat(en(null)).isNull();
	}

}
