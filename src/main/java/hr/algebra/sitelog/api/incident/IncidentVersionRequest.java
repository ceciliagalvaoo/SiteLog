package hr.algebra.sitelog.api.incident;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Body of the workflow steps ({@code start-review}, {@code sign-off}, {@code close}): the version the caller reviewed. */
@Schema(description = "Workflow step on the version the caller reviewed")
public record IncidentVersionRequest(
		@NotNull(message = "{validation.version.required}")
		@Schema(description = "Version of the incident the client last read", example = "2")
		Long version) {
}
