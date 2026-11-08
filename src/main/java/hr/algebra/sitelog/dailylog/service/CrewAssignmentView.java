package hr.algebra.sitelog.dailylog.service;

import java.math.BigDecimal;

import hr.algebra.sitelog.dailylog.domain.CrewAssignment;

/** A crew row as shown on the daily log detail and loaded into the forms. */
public record CrewAssignmentView(Long id, Long crewId, String crewName, String crewTrade, boolean crewActive,
		BigDecimal hoursWorked, int workerCount, String task) {

	static CrewAssignmentView of(CrewAssignment assignment) {
		return new CrewAssignmentView(assignment.getId(), assignment.getCrew().getId(),
				assignment.getCrew().getName(), assignment.getCrew().getTrade(), assignment.getCrew().isActive(),
				assignment.getHoursWorked(), assignment.getWorkerCount(), assignment.getTask());
	}

}
