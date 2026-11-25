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
 * One crew row nested in a work entry request. Same limits as the form and the database
 * ({@code ck_crew_assignment_hours}: 0 &lt; hours &lt;= 24 with two decimals;
 * {@code ck_crew_assignment_workers}: at least one worker).
 *
 * @param id null for a new row; the id of an existing row to change it (rows left out are removed)
 */
@Schema(description = "Crew row of a work entry")
public record CrewAssignmentRequest(
		@Schema(description = "Existing row to keep and change; omit for a new row", nullable = true)
		Long id,

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
		return new CrewAssignmentCommand(id, crewId, hoursWorked, workerCount, task);
	}

}
