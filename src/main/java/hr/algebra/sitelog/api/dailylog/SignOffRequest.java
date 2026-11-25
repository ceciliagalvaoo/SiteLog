package hr.algebra.sitelog.api.dailylog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /api/v1/daily-logs/{id}/sign-off}: the version the manager reviewed. */
@Schema(description = "Sign-off of the version the manager reviewed")
public record SignOffRequest(
		@NotNull(message = "{validation.version.required}")
		@Schema(description = "Version of the daily log the client last read", example = "3")
		Long version) {
}
