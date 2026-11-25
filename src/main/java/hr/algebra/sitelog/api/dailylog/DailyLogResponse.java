package hr.algebra.sitelog.api.dailylog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.domain.Weather;
import hr.algebra.sitelog.dailylog.service.DailyLogDetails;
import io.swagger.v3.oas.annotations.media.Schema;

/** The whole DailyLog aggregate: the log, who signed it, its work entries and crew rows. */
@Schema(description = "Daily log with its work entries and crew rows")
public record DailyLogResponse(
		Long id,
		Long siteId,
		String siteCode,
		String siteName,
		LocalDate logDate,
		@Schema(description = "DRAFT, SIGNED_OFF or REOPENED") LogStatus status,
		@Schema(description = "Incremented by every approved reopen") int revision,
		Weather weather,
		String notes,
		String signedOffByName,
		@Schema(description = "Sign-off time with the sites' UTC offset") OffsetDateTime signedOffAt,
		@Schema(description = "Optimistic-locking version: send it back on PUT, DELETE, sign-off and child changes")
		Long version,
		@Schema(description = "False when SIGNED_OFF: the log is locked until a reopen is approved") boolean editable,
		@Schema(description = "True only for a DRAFT log without incident or reopen history") boolean deletable,
		BigDecimal totalHours,
		long crewCount,
		List<WorkEntryResponse> workEntries) {

	static DailyLogResponse of(DailyLogDetails log) {
		return new DailyLogResponse(log.id(), log.siteId(), log.siteCode(), log.siteName(), log.logDate(),
				log.status(), log.revision(), log.weather(), log.notes(), log.signedOffByName(),
				log.signedOffAt() == null ? null : log.signedOffAt().toOffsetDateTime(), log.version(),
				log.editable(), log.deletable(), log.totalHours(), log.crewCount(),
				log.workEntries().stream().map(entry -> WorkEntryResponse.of(entry, log.version())).toList());
	}

}
