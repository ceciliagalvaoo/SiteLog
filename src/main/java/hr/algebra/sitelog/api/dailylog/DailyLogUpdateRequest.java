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
 * Body of {@code PUT /api/v1/daily-logs/{id}}: replaces the log and synchronises its children
 * (entries / rows with an id are changed, without an id added, left out removed).
 * {@code version} is the version the client read; a different current version means someone
 * else changed the log in the meantime (409, optimistic locking).
 */
@Schema(description = "Daily log to replace, with the version the client read")
public record DailyLogUpdateRequest(
		@NotNull(message = "{validation.version.required}")
		@Schema(description = "Version of the daily log the client last read", example = "3")
		Long version,

		@NotNull(message = "{validation.dailyLog.date.required}")
		@Schema(description = "Not in the future; one log per site and date", example = "2026-11-24")
		LocalDate logDate,

		@Schema(nullable = true, example = "CLOUDY")
		Weather weather,

		@Size(max = 4000, message = "{validation.size.max}")
		@Schema(nullable = true)
		String notes,

		@NotEmpty(message = "{validation.dailyLog.workEntries.required}")
		List<@Valid WorkEntryRequest> workEntries) {

	DailyLogCommand toCommand() {
		return DailyLogRequests.command(logDate, weather, notes, workEntries);
	}

}
