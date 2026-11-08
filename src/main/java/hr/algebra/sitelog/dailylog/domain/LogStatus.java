package hr.algebra.sitelog.dailylog.domain;

/**
 * Daily log lifecycle (AR1): {@code DRAFT -> SIGNED_OFF -> REOPENED -> SIGNED_OFF ...}.
 * Values match {@code ck_daily_log_status} in V2.
 */
public enum LogStatus {
	DRAFT,
	SIGNED_OFF,
	REOPENED;

	/** A log can be changed while it is a draft or after an approved reopen. */
	public boolean isEditable() {
		return this != SIGNED_OFF;
	}
}
