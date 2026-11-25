package hr.algebra.sitelog.dailylog.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * One changed field in the revision history: label key, value before and after (null = none).
 * Values keep their type ({@code String}, {@code Integer}, {@code BigDecimal}, {@code LocalDate},
 * {@code LocalTime}, {@code LocalDateTime} in the sites' zone, or an enum name), so the REST API
 * returns them as typed JSON; the screen formats them with {@link #display}. For an enum
 * {@code valueKeyPrefix} is set (e.g. {@code weather.}) and the screen shows
 * {@code #{prefix + value}} so it follows the UI language.
 */
public record FieldChange(String labelKey, Object before, Object after, String valueKeyPrefix) {

	/**
	 * A value as the history screen shows it, in the reader's language: dates and times with the
	 * patterns of {@code format.date} / {@code format.time} / {@code format.datetime}, hours as a
	 * localised number without trailing zeros (8.00 -> "8", 6.50 -> "6,5" in Croatian).
	 */
	public String display(Object value, String datePattern, String timePattern, String dateTimePattern,
			Locale locale) {
		return switch (value) {
			case null -> null;
			case LocalDate date -> DateTimeFormatter.ofPattern(datePattern, locale).format(date);
			case LocalTime time -> DateTimeFormatter.ofPattern(timePattern, locale).format(time);
			case LocalDateTime dateTime -> DateTimeFormatter.ofPattern(dateTimePattern, locale).format(dateTime);
			case BigDecimal number -> {
				NumberFormat format = NumberFormat.getNumberInstance(locale);
				format.setMinimumFractionDigits(0);
				format.setMaximumFractionDigits(2);
				yield format.format(number);
			}
			default -> String.valueOf(value);
		};
	}

}
