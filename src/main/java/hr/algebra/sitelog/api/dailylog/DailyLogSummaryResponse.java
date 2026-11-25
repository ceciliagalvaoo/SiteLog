package hr.algebra.sitelog.api.dailylog;

import java.math.BigDecimal;
import java.time.LocalDate;

import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.domain.Weather;
import hr.algebra.sitelog.dailylog.repository.DailyLogSummary;
import io.swagger.v3.oas.annotations.media.Schema;

/** One row of a site's daily log collection (counts computed by one aggregate query, no N+1). */
@Schema(description = "Daily log in a list")
public record DailyLogSummaryResponse(Long id, LocalDate logDate, LogStatus status, Weather weather, int revision,
		long workEntryCount, long crewCount, BigDecimal totalHours) {

	static DailyLogSummaryResponse of(DailyLogSummary log) {
		return new DailyLogSummaryResponse(log.id(), log.logDate(), log.status(), log.weather(), log.revision(),
				log.workEntryCount(), log.crewCount(), log.totalHours());
	}

}
