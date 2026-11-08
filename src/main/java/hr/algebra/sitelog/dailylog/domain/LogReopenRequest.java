package hr.algebra.sitelog.dailylog.domain;

import java.time.Instant;

import hr.algebra.sitelog.common.domain.BaseEntity;
import hr.algebra.sitelog.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Request to reopen a signed-off log (AR1): why ({@link #reason}), who asked, who decided and
 * when. The "why" layer of the reopen audit trail (ADR-0003 item 6); Envers records what changed.
 * At most one PENDING request per log ({@code uk_log_reopen_request_pending}).
 *
 * <p>Not part of the DailyLog aggregate's cascade: requests are history and are never removed
 * together with an edit of the log.
 */
@Entity
@Table(name = "log_reopen_request")
public class LogReopenRequest extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "daily_log_id", nullable = false)
	private DailyLog dailyLog;

	/** Mandatory, non-blank ({@code ck_log_reopen_request_reason}). */
	@Column(name = "reason", nullable = false)
	private String reason;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "requested_by", nullable = false)
	private AppUser requestedBy;

	/** The supervisor who approved or rejected it; null while PENDING. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "approved_by")
	private AppUser approvedBy;

	@Column(name = "requested_at", nullable = false)
	private Instant requestedAt;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 10)
	private ReopenStatus status = ReopenStatus.PENDING;

	protected LogReopenRequest() {
	}

	public LogReopenRequest(DailyLog dailyLog, String reason, AppUser requestedBy, Instant requestedAt) {
		this.dailyLog = dailyLog;
		this.reason = reason;
		this.requestedBy = requestedBy;
		this.requestedAt = requestedAt;
	}

	/** Records the decision; {@code ck_log_reopen_request_decision} requires both fields together. */
	public void decide(ReopenStatus decision, AppUser decidedBy, Instant decidedAt) {
		this.status = decision;
		this.approvedBy = decidedBy;
		this.decidedAt = decidedAt;
	}

	public DailyLog getDailyLog() {
		return dailyLog;
	}

	public String getReason() {
		return reason;
	}

	public AppUser getRequestedBy() {
		return requestedBy;
	}

	public AppUser getApprovedBy() {
		return approvedBy;
	}

	public Instant getRequestedAt() {
		return requestedAt;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public ReopenStatus getStatus() {
		return status;
	}

}
