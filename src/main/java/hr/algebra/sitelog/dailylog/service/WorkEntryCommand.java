package hr.algebra.sitelog.dailylog.service;

import java.time.LocalTime;
import java.util.List;

/** One work entry with its crew rows; {@code id} null = new entry. */
public record WorkEntryCommand(Long id, String description, String location, LocalTime startTime,
		LocalTime endTime, List<CrewAssignmentCommand> crewAssignments) {

	public WorkEntryCommand {
		description = description == null ? null : description.trim();
		location = location == null || location.isBlank() ? null : location.trim();
		crewAssignments = crewAssignments == null ? List.of() : List.copyOf(crewAssignments);
	}

}
