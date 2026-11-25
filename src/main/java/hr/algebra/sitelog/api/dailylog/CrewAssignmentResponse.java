package hr.algebra.sitelog.api.dailylog;

import java.math.BigDecimal;

import hr.algebra.sitelog.dailylog.service.CrewAssignmentView;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One crew row (association WorkEntry - Crew with its own attributes). {@code logVersion} is the
 * current version of the parent daily log, sent back as {@code logVersion} on the next change.
 */
@Schema(description = "Crew row of a work entry")
public record CrewAssignmentResponse(Long id,
		@Schema(description = "Current version of the parent daily log (send it back on the next change)") Long logVersion,
		Long crewId, String crewName, String crewTrade,
		@Schema(description = "False for a crew deactivated after it was assigned (kept in old logs)") boolean crewActive,
		BigDecimal hoursWorked, int workerCount, String task) {

	static CrewAssignmentResponse of(CrewAssignmentView row, Long logVersion) {
		return new CrewAssignmentResponse(row.id(), logVersion, row.crewId(), row.crewName(), row.crewTrade(), row.crewActive(),
				row.hoursWorked(), row.workerCount(), row.task());
	}

}
