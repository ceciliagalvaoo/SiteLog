package hr.algebra.sitelog.dailylog.web;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.format.annotation.DateTimeFormat;

import hr.algebra.sitelog.common.validation.Chronological;
import hr.algebra.sitelog.dailylog.service.WorkEntryCommand;
import hr.algebra.sitelog.dailylog.service.WorkEntryView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One work entry with its crew rows: nested in {@link DailyLogForm} ({@code workEntries[i]}) or
 * alone on the work entry page. End time after start time, as {@code ck_work_entry_times};
 * the error is shown on {@code endTime}.
 */
@Chronological(before = "startTime", after = "endTime", strict = true,
		message = "{validation.workEntry.endTime.afterStart}")
public class WorkEntryForm {

	/** Null for a new entry. */
	private Long id;

	@NotBlank(message = "{validation.workEntry.description.required}")
	@Size(max = 500, message = "{validation.size.max}")
	private String description;

	@Size(max = 150, message = "{validation.size.max}")
	private String location;

	@DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
	private LocalTime startTime;

	@DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
	private LocalTime endTime;

	@Valid
	private List<CrewAssignmentForm> crewAssignments = new ArrayList<>();

	/** An empty entry with one empty crew row (what "Add work entry" shows). */
	public static WorkEntryForm blank() {
		WorkEntryForm form = new WorkEntryForm();
		form.crewAssignments.add(new CrewAssignmentForm());
		return form;
	}

	public static WorkEntryForm from(WorkEntryView view) {
		WorkEntryForm form = new WorkEntryForm();
		form.id = view.id();
		form.description = view.description();
		form.location = view.location();
		form.startTime = view.startTime();
		form.endTime = view.endTime();
		view.crewAssignments().forEach(row -> form.crewAssignments.add(CrewAssignmentForm.from(row)));
		return form;
	}

	public WorkEntryCommand toCommand() {
		return new WorkEntryCommand(id, description, location, startTime, endTime,
				crewAssignments.stream().filter(Objects::nonNull).map(CrewAssignmentForm::toCommand).toList());
	}

	/** Crew ids chosen on this entry (to keep inactive, already assigned crews in the select). */
	List<Long> selectedCrewIds() {
		return crewAssignments.stream().filter(Objects::nonNull).map(CrewAssignmentForm::getCrewId)
			.filter(Objects::nonNull).toList();
	}

	/**
	 * Row buttons without JavaScript: {@code addCrew} adds an empty crew row,
	 * {@code removeCrew-<j>} removes row j. Returns false for an unknown action.
	 */
	boolean applyRowAction(String action) {
		if ("addCrew".equals(action)) {
			crewAssignments.add(new CrewAssignmentForm());
			return true;
		}
		if (action.startsWith("removeCrew-")) {
			int index = RowActions.index(action, 1);
			if (index >= 0 && index < crewAssignments.size()) {
				crewAssignments.remove(index);
			}
			return true;
		}
		return false;
	}

	/** Drops holes left in the list by rows removed in the browser (indexes not sent). */
	void compact() {
		crewAssignments.removeIf(Objects::isNull);
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public void setStartTime(LocalTime startTime) {
		this.startTime = startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public void setEndTime(LocalTime endTime) {
		this.endTime = endTime;
	}

	public List<CrewAssignmentForm> getCrewAssignments() {
		return crewAssignments;
	}

	public void setCrewAssignments(List<CrewAssignmentForm> crewAssignments) {
		this.crewAssignments = crewAssignments;
	}

}
