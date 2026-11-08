package hr.algebra.sitelog.dailylog.service;

import java.math.BigDecimal;

/**
 * One crew row of a work entry. {@code id} is null for a new row and set for an existing one,
 * so an update can tell "change this row" from "add a row".
 */
public record CrewAssignmentCommand(Long id, Long crewId, BigDecimal hoursWorked, Integer workerCount, String task) {

	public CrewAssignmentCommand {
		task = task == null || task.isBlank() ? null : task.trim();
	}

}
