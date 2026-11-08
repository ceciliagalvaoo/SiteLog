package hr.algebra.sitelog.incident.domain;

import java.time.Instant;
import java.time.LocalDate;

import hr.algebra.sitelog.common.domain.BaseEntity;
import hr.algebra.sitelog.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Action that prevents an incident from happening again. Child of {@link Incident}.
 * The assignee is an <b>optional</b> relation ({@code assignee_id} nullable): an action can be
 * recorded before someone is responsible for it.
 */
@Entity
@Table(name = "corrective_action")
public class CorrectiveAction extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "incident_id", nullable = false)
	private Incident incident;

	@Column(name = "description", nullable = false, length = 500)
	private String description;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assignee_id")
	private AppUser assignee;

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 10)
	private CorrectiveActionStatus status = CorrectiveActionStatus.OPEN;

	/** Set exactly when status is DONE ({@code ck_corrective_action_completed}). */
	@Column(name = "completed_at")
	private Instant completedAt;

	protected CorrectiveAction() {
	}

	public CorrectiveAction(String description, LocalDate dueDate) {
		this.description = description;
		this.dueDate = dueDate;
	}

	public Incident getIncident() {
		return incident;
	}

	void setIncident(Incident incident) {
		this.incident = incident;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public AppUser getAssignee() {
		return assignee;
	}

	public void setAssignee(AppUser assignee) {
		this.assignee = assignee;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public void setDueDate(LocalDate dueDate) {
		this.dueDate = dueDate;
	}

	public CorrectiveActionStatus getStatus() {
		return status;
	}

	public void setStatus(CorrectiveActionStatus status) {
		this.status = status;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

}
