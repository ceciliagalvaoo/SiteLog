package hr.algebra.sitelog.api.incident;

import java.time.OffsetDateTime;
import java.util.List;

import hr.algebra.sitelog.incident.domain.IncidentCategory;
import hr.algebra.sitelog.incident.domain.Severity;
import hr.algebra.sitelog.incident.service.IncidentCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/sites/{siteId}/incidents}.
 *
 * <p>The four AR2 fields ({@code rootCause}, {@code witnesses}, {@code medicalTreatment},
 * {@code authorityNotified}) have no {@code @NotBlank} here on purpose: whether they are required
 * depends on the severity, a business rule of {@code IncidentService}. A HIGH / CRITICAL report
 * without them is a well-formed request refused by that rule: 422 with the missing fields in
 * {@code errors}, not a 400.
 */
@Schema(description = "Incident to report on a site")
public record IncidentRequest(
		@NotNull(message = "{validation.incident.occurredAt.required}")
		@Schema(description = "When it happened, with its UTC offset; not in the future", example = "2026-11-24T09:30:00+01:00")
		OffsetDateTime occurredAt,

		@NotBlank(message = "{validation.incident.title.required}")
		@Size(max = 200, message = "{validation.size.max}")
		@Schema(example = "Worker slipped on wet formwork")
		String title,

		@NotBlank(message = "{validation.incident.description.required}")
		@Size(max = 5000, message = "{validation.size.max}")
		String description,

		@NotNull(message = "{validation.incident.severity.required}")
		@Schema(description = "HIGH and CRITICAL need the four extra fields and a safety supervisor sign-off", example = "HIGH")
		Severity severity,

		@NotNull(message = "{validation.incident.category.required}")
		@Schema(example = "SLIP_TRIP")
		IncidentCategory category,

		@Schema(nullable = true, description = "Optional daily log of the same site")
		Long dailyLogId,

		@Schema(nullable = true, description = "Active crews involved")
		List<@NotNull Long> affectedCrewIds,

		@Size(max = 5000, message = "{validation.size.max}")
		@Schema(nullable = true, description = "Required for HIGH / CRITICAL")
		String rootCause,

		@Size(max = 2000, message = "{validation.size.max}")
		@Schema(nullable = true, description = "Required for HIGH / CRITICAL (\"None\" if nobody saw it)")
		String witnesses,

		@Size(max = 2000, message = "{validation.size.max}")
		@Schema(nullable = true, description = "Required for HIGH / CRITICAL (\"None\" if not needed)")
		String medicalTreatment,

		@Schema(nullable = true, description = "Required for HIGH / CRITICAL")
		Boolean authorityNotified) {

	IncidentCommand toCommand() {
		return IncidentRequests.command(occurredAt, title, description, severity, category, dailyLogId,
				affectedCrewIds, rootCause, witnesses, medicalTreatment, authorityNotified);
	}

}
