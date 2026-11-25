package hr.algebra.sitelog.api.reopen;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST .../reject}: the reason is mandatory (stored as the decision note). */
@Schema(description = "Rejection of a reopen request")
public record ReopenRejectRequest(
		@NotBlank(message = "{validation.reopen.rejectReason.required}")
		@Size(max = 2000, message = "{validation.size.max}")
		@Schema(example = "The hours match the time sheets")
		String reason) {
}
