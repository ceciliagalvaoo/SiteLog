package hr.algebra.sitelog.dailylog.service;

import java.time.LocalDate;
import java.util.List;

import hr.algebra.sitelog.dailylog.domain.Weather;

/**
 * The whole daily log form: header fields plus the nested work entries and their crew rows
 * (the prototype edits the aggregate on one screen). On update the service synchronises the
 * children with this list: rows with an id are changed, rows without one are added, missing
 * rows are removed.
 *
 * <p>The list positions mirror the form ({@code workEntries[i].crewAssignments[j]}), so a
 * business error on one row is reported with that property path.
 */
public record DailyLogCommand(LocalDate logDate, Weather weather, String notes, List<WorkEntryCommand> workEntries) {

	public DailyLogCommand {
		notes = notes == null || notes.isBlank() ? null : notes.trim();
		workEntries = workEntries == null ? List.of() : List.copyOf(workEntries);
	}

}
