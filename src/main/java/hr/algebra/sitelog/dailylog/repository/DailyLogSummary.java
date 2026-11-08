package hr.algebra.sitelog.dailylog.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.domain.Weather;

/**
 * One row of a site's daily log list, computed by a single aggregate query
 * ({@link DailyLogRepository#findSummariesBySiteId}): the counts and the total hours come from
 * {@code GROUP BY}, so no work entry or crew is loaded per row (no N+1).
 */
public record DailyLogSummary(
		Long id,
		LocalDate logDate,
		LogStatus status,
		Weather weather,
		int revision,
		long workEntryCount,
		long crewCount,
		BigDecimal totalHours) {

	/** {@code sum()} is NULL for a log without assignments; the list shows 0 instead. */
	public DailyLogSummary {
		if (totalHours == null) {
			totalHours = BigDecimal.ZERO;
		}
	}

}
