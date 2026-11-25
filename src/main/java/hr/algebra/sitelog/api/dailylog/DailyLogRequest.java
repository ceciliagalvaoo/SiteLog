package hr.algebra.sitelog.api.dailylog;

import java.time.LocalDate;
import java.util.List;

import hr.algebra.sitelog.dailylog.domain.Weather;
import hr.algebra.sitelog.dailylog.service.DailyLogCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/sites/{siteId}/daily-logs}: the whole aggregate in one request,
 * as on the "New daily log" screen (log + work entries + crew rows). Same constraints and keys
 * as {@code DailyLogForm}.
 */
@Schema(description = "New daily log with its work entries and crew rows")
public record DailyLogRequest(
		@NotNull(message = "{validation.dailyLog.date.required}")
		@Schema(description = "Not in the future; one log per site and date", example = "2026-11-24")
		LocalDate logDate,

		@Schema(nullable = true, example = "CLOUDY")
		Weather weather,

		@Size(max = 4000, message = "{validation.size.max}")
		@Schema(nullable = true, example = "Delivery of rebar delayed by two hours.")
		String notes,

		@NotEmpty(message = "{validation.dailyLog.workEntries.required}")
		List<@Valid WorkEntryRequest> workEntries) {

	DailyLogCommand toCommand() {
		return DailyLogRequests.command(logDate, weather, notes, workEntries);
	}

}
