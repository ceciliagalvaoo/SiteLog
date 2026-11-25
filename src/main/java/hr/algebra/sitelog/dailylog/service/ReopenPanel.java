package hr.algebra.sitelog.dailylog.service;

import java.util.List;

import hr.algebra.sitelog.dailylog.domain.LogStatus;

/**
 * Reopen part of the daily log detail (AR1): the open request, if any, and the decided ones.
 *
 * @param pending     the PENDING request (at most one per log), or null
 * @param history     every request of the log, newest first (including the pending one)
 * @param canRequest  a reopen may be requested: the log is SIGNED_OFF and nothing is pending
 *                    (the role / own-site rule is checked separately with {@code @siteAccess})
 */
public record ReopenPanel(Long dailyLogId, LogStatus logStatus, ReopenRequestView pending,
		List<ReopenRequestView> history, boolean canRequest) {

	public boolean hasPending() {
		return pending != null;
	}

}
