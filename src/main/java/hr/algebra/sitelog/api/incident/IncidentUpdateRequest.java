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
 * Body of {@code PUT /api/v1/incidents/{id}}: the whole report plus the {@code version} the
 * client read (409 when someone changed the incident in the meantime). Same AR2 rule as
 * {@link IncidentRequest}: missing extra fields of a HIGH / CRITICAL incident are a 422.
 */
@Schema(description = "Incident to replace, with the version the client read")
public record IncidentUpdateRequest(
		@NotNull(message = "{validation.version.required}")
		@Schema(description = "Version of the incident the client last read", example = "2")
		Long version,

		@NotNull(message = "{validation.incident.occurredAt.required}")
		@Schema(example = "2026-11-24T09:30:00+01:00")
		OffsetDateTime occurredAt,

		@NotBlank(message = "{validation.incident.title.required}")
		@Size(max = 200, message = "{validation.size.max}")
		String title,

		@NotBlank(message = "{validation.incident.description.required}")
		@Size(max = 5000, message = "{validation.size.max}")
		String description,

		@NotNull(message = "{validation.incident.severity.required}")
		@Schema(description = "Only a safety supervisor may lower HIGH / CRITICAL")
		Severity severity,

		@NotNull(message = "{validation.incident.category.required}")
		IncidentCategory category,

		@Schema(nullable = true)
		Long dailyLogId,

		@Schema(nullable = true)
		List<@NotNull Long> affectedCrewIds,

		@Size(max = 5000, message = "{validation.size.max}")
		@Schema(nullable = true)
		String rootCause,

		@Size(max = 2000, message = "{validation.size.max}")
		@Schema(nullable = true)
		String witnesses,

		@Size(max = 2000, message = "{validation.size.max}")
		@Schema(nullable = true)
		String medicalTreatment,

		@Schema(nullable = true)
		Boolean authorityNotified) {

	IncidentCommand toCommand() {
		return IncidentRequests.command(occurredAt, title, description, severity, category, dailyLogId,
				affectedCrewIds, rootCause, witnesses, medicalTreatment, authorityNotified);
	}

}
