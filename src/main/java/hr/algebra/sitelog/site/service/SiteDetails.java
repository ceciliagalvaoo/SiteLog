package hr.algebra.sitelog.site.service;

import java.time.LocalDate;

/**
 * Site detail page: the site, its manager and the four overview KPIs of the prototype
 * (daily logs, logs awaiting sign-off, open incidents, open corrective actions).
 */
public record SiteDetails(
		Long id,
		String code,
		String name,
		String address,
		String description,
		LocalDate startDate,
		LocalDate endDate,
		Long managerId,
		String managerName,
		boolean finished,
		long dailyLogCount,
		long logsAwaitingSignOff,
		long openIncidentCount,
		long openCorrectiveActionCount,
		LocalDate lastLogDate) {
}
