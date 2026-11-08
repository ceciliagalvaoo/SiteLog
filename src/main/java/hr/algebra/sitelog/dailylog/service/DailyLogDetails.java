package hr.algebra.sitelog.dailylog.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import hr.algebra.sitelog.dailylog.domain.LogStatus;
import hr.algebra.sitelog.dailylog.domain.Weather;

/**
 * Daily log detail page (and the data loaded into the edit form): the log, its site, who signed
 * it, its work entries with their crew rows, and what the current state allows.
 *
 * <p>{@code signedOffAt} is a {@link ZonedDateTime} in the sites' time zone because
 * {@code #temporals.format} in the templates needs a zone ({@code Instant} has none).
 *
 * @param editable  false when SIGNED_OFF (AR1: the log is locked until a reopen is approved)
 * @param deletable true only for a DRAFT log with no incident and no reopen history
 */
public record DailyLogDetails(
		Long id,
		Long siteId,
		String siteCode,
		String siteName,
		LocalDate logDate,
		LogStatus status,
		int revision,
		Weather weather,
		String notes,
		String signedOffByName,
		ZonedDateTime signedOffAt,
		Long version,
		boolean editable,
		boolean deletable,
		List<WorkEntryView> workEntries) {

	public BigDecimal totalHours() {
		return workEntries.stream().map(WorkEntryView::totalHours).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	public long crewCount() {
		return workEntries.stream()
			.flatMap(entry -> entry.crewAssignments().stream())
			.map(CrewAssignmentView::crewId)
			.filter(Objects::nonNull)
			.distinct()
			.count();
	}

	/** Sign-off is offered on an editable log with at least one work entry. */
	public boolean canSignOff() {
		return editable && !workEntries.isEmpty();
	}

}
