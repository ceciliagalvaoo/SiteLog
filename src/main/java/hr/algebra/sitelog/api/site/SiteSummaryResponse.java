package hr.algebra.sitelog.api.site;

import java.time.LocalDate;

import hr.algebra.sitelog.site.service.SiteListItem;
import io.swagger.v3.oas.annotations.media.Schema;

/** One row of the site collection. */
@Schema(description = "Site in a list")
public record SiteSummaryResponse(Long id, String code, String name, String address, LocalDate startDate,
		LocalDate endDate, String managerName, boolean finished) {

	static SiteSummaryResponse of(SiteListItem site) {
		return new SiteSummaryResponse(site.id(), site.code(), site.name(), site.address(), site.startDate(),
				site.endDate(), site.managerName(), site.finished());
	}

}
