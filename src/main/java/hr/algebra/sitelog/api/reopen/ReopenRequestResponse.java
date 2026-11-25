package hr.algebra.sitelog.api.reopen;

import java.time.OffsetDateTime;

import hr.algebra.sitelog.dailylog.domain.ReopenStatus;
import hr.algebra.sitelog.dailylog.service.ReopenRequestView;
import io.swagger.v3.oas.annotations.media.Schema;

/** One reopen request of a daily log: why, who asked, who decided, when, and the decision note. */
@Schema(description = "Reopen request of a daily log")
public record ReopenRequestResponse(
		Long id,
		Long dailyLogId,
		@Schema(description = "PENDING, APPROVED or REJECTED") ReopenStatus status,
		String reason,
		String requestedByName,
		OffsetDateTime requestedAt,
		@Schema(nullable = true) String decidedByName,
		@Schema(nullable = true) OffsetDateTime decidedAt,
		@Schema(nullable = true, description = "Approval note, or the reason of a rejection") String decisionNote) {

	static ReopenRequestResponse of(Long dailyLogId, ReopenRequestView request) {
		return new ReopenRequestResponse(request.id(), dailyLogId, request.status(), request.reason(),
				request.requestedByName(), request.requestedAt().toOffsetDateTime(), request.decidedByName(),
				request.decidedAt() == null ? null : request.decidedAt().toOffsetDateTime(), request.decisionNote());
	}

}
