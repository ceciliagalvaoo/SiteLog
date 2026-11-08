package hr.algebra.sitelog.dailylog.service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import hr.algebra.sitelog.dailylog.domain.WorkEntry;

/** A work entry with its crew rows (detail page and forms). */
public record WorkEntryView(Long id, String description, String location, LocalTime startTime, LocalTime endTime,
		List<CrewAssignmentView> crewAssignments) {

	/** Crew hours of this entry (sum of its rows). */
	public BigDecimal totalHours() {
		return crewAssignments.stream().map(CrewAssignmentView::hoursWorked).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	/** Workers on this entry (sum of its rows). */
	public int totalWorkers() {
		return crewAssignments.stream().mapToInt(CrewAssignmentView::workerCount).sum();
	}

	static WorkEntryView of(WorkEntry entry) {
		return new WorkEntryView(entry.getId(), entry.getDescription(), entry.getLocation(), entry.getStartTime(),
				entry.getEndTime(), entry.getAssignments().stream().map(CrewAssignmentView::of).toList());
	}

}
