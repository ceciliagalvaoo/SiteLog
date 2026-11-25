package hr.algebra.sitelog.api.dailylog;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

import hr.algebra.sitelog.common.validation.Chronological;
import hr.algebra.sitelog.dailylog.service.WorkEntryCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST}/{@code PUT} on one work entry (outside the full log). {@code logVersion}
 * is the version of the daily log the client last read (409 when it changed). On PUT the crew
 * rows are synchronised like the form: rows with an id are changed, rows without one added,
 * rows left out removed.
 */
@Chronological(before = "startTime", after = "endTime", strict = true,
		message = "{validation.workEntry.endTime.afterStart}")
@Schema(description = "Work entry to create or replace")
public record WorkEntryWriteRequest(
		@NotNull(message = "{validation.version.required}")
		@Schema(description = "Version of the daily log the client last read", example = "3")
		Long logVersion,

		@NotBlank(message = "{validation.workEntry.description.required}")
		@Size(max = 500, message = "{validation.size.max}")
		@Schema(example = "Concrete pouring, slab level 2")
		String description,

		@Size(max = 150, message = "{validation.size.max}")
		@Schema(nullable = true, example = "Block B")
		String location,

		@Schema(type = "string", format = "time", nullable = true, example = "07:00:00")
		LocalTime startTime,

		@Schema(type = "string", format = "time", nullable = true, example = "15:30:00",
				description = "After startTime")
		LocalTime endTime,

		List<@Valid CrewAssignmentRequest> crewAssignments) {

	WorkEntryCommand toCommand() {
		return new WorkEntryCommand(null, description, location, startTime, endTime,
				crewAssignments == null ? List.of() : crewAssignments.stream().filter(Objects::nonNull).map(CrewAssignmentRequest::toCommand).toList());
	}

}
