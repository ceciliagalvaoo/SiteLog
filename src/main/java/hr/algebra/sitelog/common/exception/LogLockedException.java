package hr.algebra.sitelog.common.exception;

/**
 * AR1: a signed-off daily log (and its work entries and crew assignments) cannot be changed or
 * deleted; it must be reopened first (HTTP 409). The database trigger
 * {@code daily_log_lock_guard} enforces the same rule as a second layer.
 */
public class LogLockedException extends DomainException {

	private final Long dailyLogId;

	public LogLockedException(Long dailyLogId) {
		super("dailyLog.flash.locked", "daily_log " + dailyLogId + " is SIGNED_OFF");
		this.dailyLogId = dailyLogId;
	}

	public Long getDailyLogId() {
		return dailyLogId;
	}

}
