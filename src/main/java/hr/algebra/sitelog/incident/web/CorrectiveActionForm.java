package hr.algebra.sitelog.incident.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import hr.algebra.sitelog.incident.service.CorrectiveActionCommand;
import hr.algebra.sitelog.incident.service.CorrectiveActionView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corrective action form (add / edit). {@code incidentVersion} is the version of the incident
 * the page was built from: the action is a child of the incident aggregate (409 when stale).
 */
public class CorrectiveActionForm {

	@NotBlank(message = "{validation.correctiveAction.action.required}")
	@Size(max = 500, message = "{validation.size.max}")
	private String description;

	/** Optional: an action can be recorded before someone is responsible. */
	private Long assigneeId;

	@NotNull(message = "{validation.correctiveAction.dueDate.required}")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate dueDate;

	@NotNull
	private Long incidentVersion;

	public static CorrectiveActionForm blank(Long incidentVersion, LocalDate dueDate) {
		CorrectiveActionForm form = new CorrectiveActionForm();
		form.incidentVersion = incidentVersion;
		form.dueDate = dueDate;
		return form;
	}

	public static CorrectiveActionForm from(CorrectiveActionView action, Long incidentVersion) {
		CorrectiveActionForm form = new CorrectiveActionForm();
		form.description = action.description();
		form.assigneeId = action.assigneeId();
		form.dueDate = action.dueDate();
		form.incidentVersion = incidentVersion;
		return form;
	}

	public CorrectiveActionCommand toCommand() {
		return new CorrectiveActionCommand(description, assigneeId, dueDate);
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Long getAssigneeId() {
		return assigneeId;
	}

	public void setAssigneeId(Long assigneeId) {
		this.assigneeId = assigneeId;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public void setDueDate(LocalDate dueDate) {
		this.dueDate = dueDate;
	}

	public Long getIncidentVersion() {
		return incidentVersion;
	}

	public void setIncidentVersion(Long incidentVersion) {
		this.incidentVersion = incidentVersion;
	}

}
