package hr.algebra.sitelog.incident.service;

import java.time.LocalDate;

/** Data of a corrective action; the assignee is optional. */
public record CorrectiveActionCommand(String description, Long assigneeId, LocalDate dueDate) {
}
