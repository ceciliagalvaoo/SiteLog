package hr.algebra.sitelog.api.dailylog;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import hr.algebra.sitelog.dailylog.service.WorkEntryView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One work entry with its crew rows. {@code logVersion} is the current version of the parent
 * daily log: a client sends it back as {@code logVersion} on its next change to this log.
 */
@Schema(description = "Work entry with its crew rows")
public record WorkEntryResponse(Long id,
		@Schema(description = "Current version of the parent daily log (send it back on the next change)") Long logVersion,
		String description, String location,
		@Schema(type = "string", format = "time") LocalTime startTime,
		@Schema(type = "string", format = "time") LocalTime endTime,
		@Schema(description = "Sum of the crew rows' hours") BigDecimal totalHours,
		@Schema(description = "Sum of the crew rows' workers") int totalWorkers,
		List<CrewAssignmentResponse> crewAssignments) {

	static WorkEntryResponse of(WorkEntryView entry, Long logVersion) {
		return new WorkEntryResponse(entry.id(), logVersion, entry.description(), entry.location(),
				entry.startTime(), entry.endTime(), entry.totalHours(), entry.totalWorkers(),
				entry.crewAssignments().stream().map(row -> CrewAssignmentResponse.of(row, logVersion)).toList());
	}

}
