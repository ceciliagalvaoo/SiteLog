package hr.algebra.sitelog.api.site;

import java.time.LocalDate;

import hr.algebra.sitelog.site.service.SiteDetails;
import io.swagger.v3.oas.annotations.media.Schema;

/** One site with its manager and the overview counters of the site detail page. */
@Schema(description = "Site with its overview counters")
public record SiteResponse(
		Long id,
		String code,
		String name,
		String address,
		String description,
		LocalDate startDate,
		LocalDate endDate,
		Long managerId,
		String managerName,
		@Schema(description = "True once the end date is reached") boolean finished,
		long dailyLogCount,
		@Schema(description = "Daily logs in DRAFT or REOPENED") long logsAwaitingSignOff,
		long openIncidentCount,
		long openCorrectiveActionCount,
		LocalDate lastLogDate) {

	static SiteResponse of(SiteDetails site) {
		return new SiteResponse(site.id(), site.code(), site.name(), site.address(), site.description(),
				site.startDate(), site.endDate(), site.managerId(), site.managerName(), site.finished(),
				site.dailyLogCount(), site.logsAwaitingSignOff(), site.openIncidentCount(),
				site.openCorrectiveActionCount(), site.lastLogDate());
	}

}
