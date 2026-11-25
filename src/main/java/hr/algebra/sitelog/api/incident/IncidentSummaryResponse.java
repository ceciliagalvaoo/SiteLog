package hr.algebra.sitelog.api.incident;

import java.time.OffsetDateTime;

import hr.algebra.sitelog.incident.domain.IncidentCategory;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import hr.algebra.sitelog.incident.domain.Severity;
import hr.algebra.sitelog.incident.service.IncidentListItem;
import io.swagger.v3.oas.annotations.media.Schema;

/** One row of an incident collection. */
@Schema(description = "Incident row of a list")
public record IncidentSummaryResponse(Long id, Long siteId, String siteCode, String siteName,
		OffsetDateTime occurredAt, String title, Severity severity, IncidentCategory category, IncidentStatus status,
		String reportedByName) {

	static IncidentSummaryResponse of(IncidentListItem item) {
		return new IncidentSummaryResponse(item.id(), item.siteId(), item.siteCode(), item.siteName(),
				item.occurredAt().toOffsetDateTime(), item.title(), item.severity(), item.category(), item.status(),
				item.reportedByName());
	}

}
