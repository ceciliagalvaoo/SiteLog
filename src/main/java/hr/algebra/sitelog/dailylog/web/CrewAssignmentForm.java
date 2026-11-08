package hr.algebra.sitelog.dailylog.web;

import java.math.BigDecimal;

import hr.algebra.sitelog.dailylog.service.CrewAssignmentCommand;
import hr.algebra.sitelog.dailylog.service.CrewAssignmentView;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One crew row: nested in {@link WorkEntryForm} ({@code workEntries[i].crewAssignments[j]}) or
 * alone on the crew row page. Same limits as {@code ck_crew_assignment_hours} (0 < h <= 24, two
 * decimals) and {@code ck_crew_assignment_workers} (>= 1).
 */
public class CrewAssignmentForm {

	/** Null for a new row. */
	private Long id;

	@NotNull(message = "{validation.crewAssignment.crew.required}")
	private Long crewId;

	@NotNull(message = "{validation.crewAssignment.hoursWorked.range}")
	@DecimalMin(value = "0", inclusive = false, message = "{validation.crewAssignment.hoursWorked.range}")
	@DecimalMax(value = "24", message = "{validation.crewAssignment.hoursWorked.range}")
	@Digits(integer = 2, fraction = 2, message = "{validation.crewAssignment.hoursWorked.range}")
	private BigDecimal hoursWorked;

	@NotNull(message = "{validation.crewAssignment.workerCount.min}")
	@Min(value = 1, message = "{validation.crewAssignment.workerCount.min}")
	private Integer workerCount;

	@Size(max = 255, message = "{validation.size.max}")
	private String task;

	public static CrewAssignmentForm from(CrewAssignmentView view) {
		CrewAssignmentForm form = new CrewAssignmentForm();
		form.id = view.id();
		form.crewId = view.crewId();
		form.hoursWorked = view.hoursWorked();
		form.workerCount = view.workerCount();
		form.task = view.task();
		return form;
	}

	public CrewAssignmentCommand toCommand() {
		return new CrewAssignmentCommand(id, crewId, hoursWorked, workerCount, task);
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getCrewId() {
		return crewId;
	}

	public void setCrewId(Long crewId) {
		this.crewId = crewId;
	}

	public BigDecimal getHoursWorked() {
		return hoursWorked;
	}

	public void setHoursWorked(BigDecimal hoursWorked) {
		this.hoursWorked = hoursWorked;
	}

	public Integer getWorkerCount() {
		return workerCount;
	}

	public void setWorkerCount(Integer workerCount) {
		this.workerCount = workerCount;
	}

	public String getTask() {
		return task;
	}

	public void setTask(String task) {
		this.task = task;
	}

}
