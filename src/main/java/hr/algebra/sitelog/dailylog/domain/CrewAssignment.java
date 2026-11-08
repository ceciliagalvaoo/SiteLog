package hr.algebra.sitelog.dailylog.domain;

import java.math.BigDecimal;

import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import hr.algebra.sitelog.common.domain.BaseEntity;
import hr.algebra.sitelog.crew.domain.Crew;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Association entity of the many-to-many {@code WorkEntry *-* Crew} (ADR-0001 item 4). It is an
 * entity, not a {@code @ManyToMany} join table, because the link has its own data: hours
 * worked, number of workers and the task.
 *
 * <p>Surrogate {@code id} plus the unique constraint {@code (work_entry_id, crew_id)} instead of
 * a composite key: the database still rejects the same crew twice on one work entry, while
 * equals/hashCode, REST URLs and nested form rows stay simple.
 */
@Entity
@Table(name = "crew_assignment", uniqueConstraints = @UniqueConstraint(
		name = "uk_crew_assignment_entry_crew", columnNames = { "work_entry_id", "crew_id" }))
@Audited
public class CrewAssignment extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "work_entry_id", nullable = false)
	private WorkEntry workEntry;

	@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "crew_id", nullable = false)
	private Crew crew;

	/** Greater than 0 and at most 24 ({@code ck_crew_assignment_hours}), two decimals. */
	@Column(name = "hours_worked", nullable = false, precision = 4, scale = 2)
	private BigDecimal hoursWorked;

	/** At least 1 ({@code ck_crew_assignment_workers}). */
	@Column(name = "worker_count", nullable = false)
	private int workerCount;

	@Column(name = "task")
	private String task;

	protected CrewAssignment() {
	}

	public CrewAssignment(Crew crew, BigDecimal hoursWorked, int workerCount, String task) {
		this.crew = crew;
		this.hoursWorked = hoursWorked;
		this.workerCount = workerCount;
		this.task = task;
	}

	public WorkEntry getWorkEntry() {
		return workEntry;
	}

	void setWorkEntry(WorkEntry workEntry) {
		this.workEntry = workEntry;
	}

	public Crew getCrew() {
		return crew;
	}

	public void setCrew(Crew crew) {
		this.crew = crew;
	}

	public BigDecimal getHoursWorked() {
		return hoursWorked;
	}

	public void setHoursWorked(BigDecimal hoursWorked) {
		this.hoursWorked = hoursWorked;
	}

	public int getWorkerCount() {
		return workerCount;
	}

	public void setWorkerCount(int workerCount) {
		this.workerCount = workerCount;
	}

	public String getTask() {
		return task;
	}

	public void setTask(String task) {
		this.task = task;
	}

}
