package hr.algebra.sitelog.api.incident;

import java.time.LocalDate;

import hr.algebra.sitelog.incident.service.CorrectiveActionCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST} / {@code PUT} of a corrective action. The action is a child of the
 * incident aggregate, so the body carries the incident's version (409 when stale).
 */
@Schema(description = "Corrective action to add or replace")
public record CorrectiveActionRequest(
		@NotNull(message = "{validation.version.required}")
		@Schema(description = "Version of the incident the client last read", example = "3")
		Long incidentVersion,

		@NotBlank(message = "{validation.correctiveAction.action.required}")
		@Size(max = 500, message = "{validation.size.max}")
		@Schema(example = "Install anti-slip mats on the formwork deck")
		String description,

		@Schema(nullable = true, description = "Active site manager or safety supervisor")
		Long assigneeId,

		@NotNull(message = "{validation.correctiveAction.dueDate.required}")
		@Schema(description = "Today or later (an unchanged past date of an overdue action may stay)", example = "2026-12-01")
		LocalDate dueDate) {

	CorrectiveActionCommand toCommand() {
		return new CorrectiveActionCommand(description, assigneeId, dueDate);
	}

}
