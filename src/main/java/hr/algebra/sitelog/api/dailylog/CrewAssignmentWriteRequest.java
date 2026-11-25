package hr.algebra.sitelog.api.dailylog;

import java.math.BigDecimal;

import hr.algebra.sitelog.dailylog.service.CrewAssignmentCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST}/{@code PUT} on one crew row. {@code logVersion} is the version of the
 * daily log the client last read: every change to a child also increments the log's version,
 * so a concurrent edit of the same log - on any child - is detected (409).
 */
@Schema(description = "Crew row to create or replace")
public record CrewAssignmentWriteRequest(
		@NotNull(message = "{validation.version.required}")
		@Schema(description = "Version of the daily log the client last read", example = "3")
		Long logVersion,

		@NotNull(message = "{validation.crewAssignment.crew.required}")
		@Schema(description = "Active crew", example = "1")
		Long crewId,

		@NotNull(message = "{validation.crewAssignment.hoursWorked.range}")
		@DecimalMin(value = "0", inclusive = false, message = "{validation.crewAssignment.hoursWorked.range}")
		@DecimalMax(value = "24", message = "{validation.crewAssignment.hoursWorked.range}")
		@Digits(integer = 2, fraction = 2, message = "{validation.crewAssignment.hoursWorked.range}")
		@Schema(example = "7.5")
		BigDecimal hoursWorked,

		@NotNull(message = "{validation.crewAssignment.workerCount.min}")
		@Min(value = 1, message = "{validation.crewAssignment.workerCount.min}")
		@Schema(example = "4")
		Integer workerCount,

		@Size(max = 255, message = "{validation.size.max}")
		@Schema(nullable = true, example = "Formwork for the second floor slab")
		String task) {

	CrewAssignmentCommand toCommand() {
		return new CrewAssignmentCommand(null, crewId, hoursWorked, workerCount, task);
	}

}
