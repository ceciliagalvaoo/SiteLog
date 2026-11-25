package hr.algebra.sitelog.api.incident;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Body of {@code POST .../corrective-actions/{id}/done}. */
@Schema(description = "Mark a corrective action as done on the incident version the caller read")
public record CorrectiveActionDoneRequest(
		@NotNull(message = "{validation.version.required}")
		@Schema(description = "Version of the incident the client last read", example = "3")
		Long incidentVersion) {
}
