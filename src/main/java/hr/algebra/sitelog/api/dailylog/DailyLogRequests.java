package hr.algebra.sitelog.api.dailylog;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import hr.algebra.sitelog.dailylog.domain.Weather;
import hr.algebra.sitelog.dailylog.service.DailyLogCommand;

/** Request -> command mapping shared by the create and the update body. */
final class DailyLogRequests {

	private DailyLogRequests() {
	}

	static DailyLogCommand command(LocalDate logDate, Weather weather, String notes, List<WorkEntryRequest> entries) {
		return new DailyLogCommand(logDate, weather, notes, entries == null ? List.of()
				: entries.stream().filter(Objects::nonNull).map(WorkEntryRequest::toCommand).toList());
	}

}
