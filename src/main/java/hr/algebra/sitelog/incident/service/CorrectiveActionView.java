package hr.algebra.sitelog.incident.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import hr.algebra.sitelog.incident.domain.CorrectiveAction;
import hr.algebra.sitelog.incident.domain.CorrectiveActionStatus;

/**
 * A corrective action on the incident detail and in its form.
 *
 * @param overdue true when it is OVERDUE, or still OPEN after its due date (before the nightly
 *                job marks it, M4) - the screen shows it as overdue either way
 */
public record CorrectiveActionView(Long id, String description, Long assigneeId, String assigneeName,
		LocalDate dueDate, CorrectiveActionStatus status, ZonedDateTime completedAt, boolean overdue) {

	static CorrectiveActionView of(CorrectiveAction action, LocalDate today, ZoneId zone) {
		boolean overdue = action.getStatus() == CorrectiveActionStatus.OVERDUE
				|| (action.getStatus() == CorrectiveActionStatus.OPEN && action.getDueDate().isBefore(today));
		return new CorrectiveActionView(action.getId(), action.getDescription(),
				action.getAssignee() == null ? null : action.getAssignee().getId(),
				action.getAssignee() == null ? null : action.getAssignee().getFullName(), action.getDueDate(),
				action.getStatus(), action.getCompletedAt() == null ? null : action.getCompletedAt().atZone(zone),
				overdue);
	}

	public boolean done() {
		return status == CorrectiveActionStatus.DONE;
	}

}
