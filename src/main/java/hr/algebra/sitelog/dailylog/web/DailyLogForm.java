package hr.algebra.sitelog.dailylog.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.format.annotation.DateTimeFormat;

import hr.algebra.sitelog.dailylog.domain.Weather;
import hr.algebra.sitelog.dailylog.service.DailyLogCommand;
import hr.algebra.sitelog.dailylog.service.DailyLogDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Backing object of {@code dailylog/form}: the log and, nested as in the prototype, its work
 * entries ({@code workEntries[i]}) each with its crew rows ({@code workEntries[i].crewAssignments[j]}).
 * {@code @Valid} cascades validation into both lists.
 *
 * <p>{@link #version} is the log version the form was loaded with (hidden field); the service
 * refuses the save when the log changed in the meantime (409).
 */
public class DailyLogForm {

	private Long version;

	@NotNull(message = "{validation.dailyLog.date.required}")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate logDate;

	private Weather weather;

	@Size(max = 4000, message = "{validation.size.max}")
	private String notes;

	@Valid
	@NotEmpty(message = "{validation.dailyLog.workEntries.required}")
	private List<WorkEntryForm> workEntries = new ArrayList<>();

	/** New log for a date, with one empty work entry and one empty crew row. */
	public static DailyLogForm blank(LocalDate logDate) {
		DailyLogForm form = new DailyLogForm();
		form.logDate = logDate;
		form.workEntries.add(WorkEntryForm.blank());
		return form;
	}

	public static DailyLogForm from(DailyLogDetails log) {
		DailyLogForm form = new DailyLogForm();
		form.version = log.version();
		form.logDate = log.logDate();
		form.weather = log.weather();
		form.notes = log.notes();
		log.workEntries().forEach(entry -> form.workEntries.add(WorkEntryForm.from(entry)));
		return form;
	}

	public DailyLogCommand toCommand() {
		return new DailyLogCommand(logDate, weather, notes,
				workEntries.stream().filter(Objects::nonNull).map(WorkEntryForm::toCommand).toList());
	}

	List<Long> selectedCrewIds() {
		return workEntries.stream().filter(Objects::nonNull).flatMap(entry -> entry.selectedCrewIds().stream())
			.toList();
	}

	/**
	 * Row buttons: {@code addEntry}, {@code removeEntry-<i>}, {@code addCrew-<i>},
	 * {@code removeCrew-<i>-<j>}. Returns false for an unknown action.
	 */
	boolean applyRowAction(String action) {
		if ("addEntry".equals(action)) {
			workEntries.add(WorkEntryForm.blank());
			return true;
		}
		int i = RowActions.index(action, 1);
		boolean validEntry = i >= 0 && i < workEntries.size();
		if (action.startsWith("removeEntry-")) {
			if (validEntry) {
				workEntries.remove(i);
			}
			return true;
		}
		if (action.startsWith("addCrew-")) {
			if (validEntry) {
				workEntries.get(i).applyRowAction("addCrew");
			}
			return true;
		}
		if (action.startsWith("removeCrew-")) {
			if (validEntry) {
				workEntries.get(i).applyRowAction("removeCrew-" + RowActions.index(action, 2));
			}
			return true;
		}
		return false;
	}

	/** Drops holes left by rows removed in the browser. */
	void compact() {
		workEntries.removeIf(Objects::isNull);
		workEntries.forEach(WorkEntryForm::compact);
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

	public LocalDate getLogDate() {
		return logDate;
	}

	public void setLogDate(LocalDate logDate) {
		this.logDate = logDate;
	}

	public Weather getWeather() {
		return weather;
	}

	public void setWeather(Weather weather) {
		this.weather = weather;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public List<WorkEntryForm> getWorkEntries() {
		return workEntries;
	}

	public void setWorkEntries(List<WorkEntryForm> workEntries) {
		this.workEntries = workEntries;
	}

}
