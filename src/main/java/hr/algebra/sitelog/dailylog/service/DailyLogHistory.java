package hr.algebra.sitelog.dailylog.service;

import java.util.List;

import hr.algebra.sitelog.audit.service.DomainActionView;

/**
 * "Revision history" page of a daily log - the three layers of the audit trail (ADR-0003):
 *
 * @param log            header of the log (site, date, status, version)
 * @param revision       current reopen counter of the log
 * @param revisions      Envers revisions, newest first: what changed, before and after, who, when
 * @param reopenRequests why the log was reopened and who decided (newest first)
 * @param actions        recorded domain actions (sign-off, reopen request / approval / rejection), oldest first
 */
public record DailyLogHistory(DailyLogHeader log, int revision, List<RevisionView> revisions,
		List<ReopenRequestView> reopenRequests, List<DomainActionView> actions) {
}
