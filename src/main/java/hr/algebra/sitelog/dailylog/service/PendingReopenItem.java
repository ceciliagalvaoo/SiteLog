package hr.algebra.sitelog.dailylog.service;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/** One row of the supervisor's queue of pending reopen requests, oldest first. */
public record PendingReopenItem(Long requestId, Long dailyLogId, String siteCode, String siteName, LocalDate logDate,
		String reason, String requestedByName, ZonedDateTime requestedAt) {
}
