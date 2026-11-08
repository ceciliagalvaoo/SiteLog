package hr.algebra.sitelog.dailylog.service;

import java.time.LocalDate;

import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.LogStatus;

/**
 * The log a child page belongs to (work entry / crew assignment forms): enough for the page
 * header, the breadcrumb back to the log and the hidden {@code logVersion} field.
 */
public record DailyLogHeader(Long id, Long siteId, String siteCode, String siteName, LocalDate logDate,
		LogStatus status, Long version) {

	public boolean editable() {
		return status.isEditable();
	}

	static DailyLogHeader of(DailyLog log) {
		return new DailyLogHeader(log.getId(), log.getSite().getId(), log.getSite().getCode(), log.getSite().getName(),
				log.getLogDate(), log.getStatus(), log.getVersion());
	}

}
