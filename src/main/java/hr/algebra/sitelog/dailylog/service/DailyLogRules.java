package hr.algebra.sitelog.dailylog.service;

import java.util.Objects;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

import hr.algebra.sitelog.common.exception.LogLockedException;
import hr.algebra.sitelog.dailylog.domain.DailyLog;

/** Checks every write to the DailyLog aggregate runs first (root or child). */
final class DailyLogRules {

	private DailyLogRules() {
	}

	/**
	 * Optimistic lock across requests: the form carries the version the user saw. If the log
	 * changed since (another save, a sign-off), the write is refused with the same exception
	 * Hibernate throws for a concurrent commit, so both cases end as 409 / "changed by someone
	 * else". Within the transaction, {@code @Version} (plus OPTIMISTIC_FORCE_INCREMENT for
	 * children) catches the remaining race between this check and the commit.
	 */
	static void requireVersion(DailyLog log, Long expectedVersion) {
		if (!Objects.equals(log.getVersion(), expectedVersion)) {
			throw new ObjectOptimisticLockingFailureException(DailyLog.class, log.getId());
		}
	}

	/** AR1 part 1: a signed-off log and its children are read-only (the V3 trigger is the second layer). */
	static void requireEditable(DailyLog log) {
		if (!log.getStatus().isEditable()) {
			throw new LogLockedException(log.getId());
		}
	}

}
