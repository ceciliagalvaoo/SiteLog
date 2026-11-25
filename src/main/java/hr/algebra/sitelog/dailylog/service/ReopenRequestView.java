package hr.algebra.sitelog.dailylog.service;

import java.time.ZonedDateTime;

import hr.algebra.sitelog.dailylog.domain.LogReopenRequest;
import hr.algebra.sitelog.dailylog.domain.ReopenStatus;

/**
 * One reopen request as the log detail and the history show it: why, who asked, who decided,
 * when, and the supervisor's note (the reason of a rejection). Times in the sites' zone.
 */
public record ReopenRequestView(Long id, ReopenStatus status, String reason, String requestedByName,
		ZonedDateTime requestedAt, String decidedByName, ZonedDateTime decidedAt, String decisionNote) {

	static ReopenRequestView of(LogReopenRequest request, java.time.ZoneId zone) {
		return new ReopenRequestView(request.getId(), request.getStatus(), request.getReason(),
				request.getRequestedBy().getFullName(), request.getRequestedAt().atZone(zone),
				request.getApprovedBy() == null ? null : request.getApprovedBy().getFullName(),
				request.getDecidedAt() == null ? null : request.getDecidedAt().atZone(zone), request.getDecisionNote());
	}

	public boolean pending() {
		return status == ReopenStatus.PENDING;
	}

}
