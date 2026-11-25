package hr.algebra.sitelog.api.reopen;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/daily-logs/{id}/reopen-requests}: why, and the log version the manager read. */
@Schema(description = "Request to reopen a signed-off daily log")
public record ReopenRequestCreateRequest(
		@NotNull(message = "{validation.version.required}")
		@Schema(description = "Version of the daily log the client last read", example = "4")
		Long version,

		@NotBlank(message = "{validation.dailyLog.reopenReason.required}")
		@Size(max = 2000, message = "{validation.size.max}")
		@Schema(example = "Crew hours of the concrete crew were entered twice")
		String reason) {
}
