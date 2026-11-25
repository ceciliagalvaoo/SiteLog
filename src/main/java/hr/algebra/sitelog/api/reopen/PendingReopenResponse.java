package hr.algebra.sitelog.api.reopen;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import hr.algebra.sitelog.dailylog.service.PendingReopenItem;
import io.swagger.v3.oas.annotations.media.Schema;

/** One row of the safety supervisors' queue of pending reopen requests. */
@Schema(description = "Pending reopen request waiting for a supervisor's decision")
public record PendingReopenResponse(Long id, Long dailyLogId, String siteCode, String siteName, LocalDate logDate,
		String reason, String requestedByName, OffsetDateTime requestedAt) {

	static PendingReopenResponse of(PendingReopenItem item) {
		return new PendingReopenResponse(item.requestId(), item.dailyLogId(), item.siteCode(), item.siteName(),
				item.logDate(), item.reason(), item.requestedByName(), item.requestedAt().toOffsetDateTime());
	}

}
