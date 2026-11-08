package hr.algebra.sitelog.dailylog.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.crew.domain.Crew;
import hr.algebra.sitelog.crew.repository.CrewRepository;
import hr.algebra.sitelog.dailylog.domain.CrewAssignment;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.WorkEntry;

/**
 * Applies commands to the entities of the DailyLog aggregate: synchronises the nested lists
 * (update rows with an id, add rows without one, remove the missing ones; {@code orphanRemoval}
 * deletes them at flush) and checks the crew of each row.
 *
 * <p>Runs inside the caller's transaction; the caller has already checked version and lock.
 */
@Component
class DailyLogAssembler {

	static final String CREW_INVALID = "validation.crewAssignment.crew.invalid";

	static final String CREW_DUPLICATE = "validation.crewAssignment.crew.duplicate";

	private final CrewRepository crews;

	DailyLogAssembler(CrewRepository crews) {
		this.crews = crews;
	}

	/** Synchronises all work entries of a log with the form (path prefix {@code workEntries[i].}). */
	void syncWorkEntries(DailyLog log, List<WorkEntryCommand> commands) {
		Map<Long, Crew> crewCache = loadCrews(commands.stream().flatMap(c -> c.crewAssignments().stream()).toList());
		Map<Long, WorkEntry> existing = log.getWorkEntries().stream()
			.collect(Collectors.toMap(WorkEntry::getId, Function.identity()));
		Set<WorkEntry> kept = new HashSet<>();
		List<WorkEntry> ordered = new ArrayList<>();
		for (WorkEntryCommand command : commands) {
			WorkEntry entry;
			if (command.id() == null) {
				entry = new WorkEntry(command.description());
			}
			else {
				entry = existing.get(command.id());
				if (entry == null) {
					throw new NotFoundException(WorkEntry.class, command.id());
				}
				kept.add(entry);
			}
			ordered.add(entry);
		}
		existing.values().stream().filter(entry -> !kept.contains(entry)).toList().forEach(log::removeWorkEntry);
		for (int i = 0; i < commands.size(); i++) {
			WorkEntry entry = ordered.get(i);
			apply(entry, commands.get(i), "workEntries[" + i + "].", crewCache);
			if (entry.getDailyLog() == null) {
				log.addWorkEntry(entry);
			}
		}
	}

	/** Applies one work entry command (fields + crew rows) to a new or existing entry. */
	void apply(WorkEntry entry, WorkEntryCommand command, String pathPrefix) {
		apply(entry, command, pathPrefix, loadCrews(command.crewAssignments()));
	}

	private void apply(WorkEntry entry, WorkEntryCommand command, String pathPrefix, Map<Long, Crew> crewCache) {
		entry.setDescription(command.description());
		entry.setLocation(command.location());
		entry.setStartTime(command.startTime());
		entry.setEndTime(command.endTime());
		syncAssignments(entry, command.crewAssignments(), pathPrefix, crewCache);
	}

	/**
	 * Synchronises the crew rows of one entry. A new row for a crew whose old row is being removed
	 * reuses that row: Hibernate flushes INSERTs before DELETEs, so "remove crew A, add crew A"
	 * would otherwise hit {@code uk_crew_assignment_entry_crew}.
	 */
	private void syncAssignments(WorkEntry entry, List<CrewAssignmentCommand> commands, String pathPrefix,
			Map<Long, Crew> crewCache) {
		Map<Long, CrewAssignment> existing = entry.getAssignments().stream()
			.filter(a -> a.getId() != null)
			.collect(Collectors.toMap(CrewAssignment::getId, Function.identity()));
		Set<Long> seenCrews = new HashSet<>();
		Set<CrewAssignment> kept = new HashSet<>();
		List<CrewAssignment> targets = new ArrayList<>();
		for (int j = 0; j < commands.size(); j++) {
			CrewAssignmentCommand command = commands.get(j);
			if (command.crewId() != null && !seenCrews.add(command.crewId())) {
				throw new InvalidFieldException(crewPath(pathPrefix, j), CREW_DUPLICATE);
			}
			CrewAssignment target = null;
			if (command.id() != null) {
				target = existing.get(command.id());
				if (target == null) {
					throw new NotFoundException(CrewAssignment.class, command.id());
				}
				kept.add(target);
			}
			targets.add(target);
		}
		List<CrewAssignment> leftovers = new ArrayList<>(
				existing.values().stream().filter(a -> !kept.contains(a)).toList());
		for (int j = 0; j < commands.size(); j++) {
			if (targets.get(j) == null) {
				Long crewId = commands.get(j).crewId();
				for (CrewAssignment leftover : leftovers) {
					if (leftover.getCrew().getId().equals(crewId)) {
						leftovers.remove(leftover);
						targets.set(j, leftover);
						break;
					}
				}
			}
		}
		leftovers.forEach(entry::removeAssignment);
		for (int j = 0; j < commands.size(); j++) {
			CrewAssignmentCommand command = commands.get(j);
			CrewAssignment target = targets.get(j);
			Crew crew = resolveCrew(command.crewId(), target == null ? null : target.getCrew(),
					crewPath(pathPrefix, j), crewCache);
			if (target == null) {
				entry.addAssignment(new CrewAssignment(crew, command.hoursWorked(), command.workerCount(), command.task()));
			}
			else {
				target.setCrew(crew);
				target.setHoursWorked(command.hoursWorked());
				target.setWorkerCount(command.workerCount());
				target.setTask(command.task());
			}
		}
	}

	/**
	 * The crew of a row must exist and be active when it is newly chosen. A row that keeps its
	 * current crew stays valid even if that crew was deactivated later (history is kept).
	 */
	Crew resolveCrew(Long crewId, Crew current, String path, Map<Long, Crew> crewCache) {
		if (crewId == null) {
			throw new InvalidFieldException(path, CREW_INVALID);
		}
		if (current != null && Objects.equals(current.getId(), crewId)) {
			return current;
		}
		Crew crew = crewCache.computeIfAbsent(crewId, id -> crews.findById(id).orElse(null));
		if (crew == null || !crew.isActive()) {
			throw new InvalidFieldException(path, CREW_INVALID);
		}
		return crew;
	}

	/** One query for all crews referenced by the form instead of one per row. */
	Map<Long, Crew> loadCrews(List<CrewAssignmentCommand> commands) {
		Set<Long> ids = commands.stream().map(CrewAssignmentCommand::crewId).filter(Objects::nonNull)
			.collect(Collectors.toSet());
		if (ids.isEmpty()) {
			return new HashMap<>();
		}
		return crews.findAllById(ids).stream()
			.collect(Collectors.toMap(Crew::getId, Function.identity(), (a, b) -> a, HashMap::new));
	}

	private static String crewPath(String pathPrefix, int index) {
		return pathPrefix + "crewAssignments[" + index + "].crewId";
	}

}
