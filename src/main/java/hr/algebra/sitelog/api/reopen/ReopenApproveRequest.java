package hr.algebra.sitelog.api.reopen;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Optional body of {@code POST .../approve}: a note of the supervisor. */
@Schema(description = "Approval of a reopen request, with an optional note")
public record ReopenApproveRequest(
		@Size(max = 2000, message = "{validation.size.max}")
		@Schema(nullable = true, example = "Correct the hours and sign again today")
		String note) {
}
