package hr.algebra.sitelog.api.dailylog;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

import hr.algebra.sitelog.common.validation.Chronological;
import hr.algebra.sitelog.dailylog.service.WorkEntryCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One work entry nested in a daily log request ({@code workEntries[i]}), with its crew rows
 * ({@code workEntries[i].crewAssignments[j]}). The names mirror the form, so a business error
 * on a row comes back with the same property path the client sent.
 *
 * @param id null for a new entry; the id of an existing entry to keep and change it (on PUT,
 *           entries left out are removed with their crew rows)
 */
@Chronological(before = "startTime", after = "endTime", strict = true,
		message = "{validation.workEntry.endTime.afterStart}")
@Schema(description = "Work entry of a daily log")
public record WorkEntryRequest(
		@Schema(description = "Existing entry to keep and change; omit for a new entry", nullable = true)
		Long id,

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
		return new WorkEntryCommand(id, description, location, startTime, endTime,
				crewAssignments == null ? List.of() : crewAssignments.stream().filter(Objects::nonNull).map(CrewAssignmentRequest::toCommand).toList());
	}

}
