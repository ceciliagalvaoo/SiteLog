package hr.algebra.sitelog.dailylog.service;

import java.util.HashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.crew.domain.Crew;
import hr.algebra.sitelog.dailylog.domain.CrewAssignment;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.WorkEntry;
import hr.algebra.sitelog.dailylog.repository.CrewAssignmentRepository;
import hr.algebra.sitelog.dailylog.repository.DailyLogRepository;
import hr.algebra.sitelog.dailylog.repository.WorkEntryRepository;

/**
 * One crew row ({@link CrewAssignment}, the association entity WorkEntry *-* Crew) at a time.
 * Same write rules as {@link WorkEntryService}: parent log loaded with
 * OPTIMISTIC_FORCE_INCREMENT, version the user saw checked, locked after sign-off.
 */
@Service
@Transactional(readOnly = true)
public class CrewAssignmentService {

	private final DailyLogRepository dailyLogs;

	private final WorkEntryRepository workEntries;

	private final CrewAssignmentRepository assignments;

	private final DailyLogAssembler assembler;

	public CrewAssignmentService(DailyLogRepository dailyLogs, WorkEntryRepository workEntries,
			CrewAssignmentRepository assignments, DailyLogAssembler assembler) {
		this.dailyLogs = dailyLogs;
		this.workEntries = workEntries;
		this.assignments = assignments;
		this.assembler = assembler;
	}

	public CrewAssignmentView get(Long dailyLogId, Long assignmentId) {
		return CrewAssignmentView.of(loadAssignment(dailyLogId, assignmentId));
	}

	/** Id of the work entry a row belongs to (breadcrumb of the crew row form). */
	public Long workEntryIdOf(Long dailyLogId, Long assignmentId) {
		return loadAssignment(dailyLogId, assignmentId).getWorkEntry().getId();
	}

	@Transactional
	public Long create(Long dailyLogId, Long workEntryId, Long expectedLogVersion, CrewAssignmentCommand command) {
		loadLogForChange(dailyLogId, expectedLogVersion);
		WorkEntry entry = workEntries.findDetailById(workEntryId)
			.filter(e -> e.getDailyLog().getId().equals(dailyLogId))
			.orElseThrow(() -> new NotFoundException(WorkEntry.class, workEntryId));
		if (command.crewId() != null && assignments.existsByWorkEntry_IdAndCrew_Id(workEntryId, command.crewId())) {
			throw new InvalidFieldException("crewId", DailyLogAssembler.CREW_DUPLICATE);
		}
		Crew crew = assembler.resolveCrew(command.crewId(), null, "crewId", new HashMap<>());
		CrewAssignment assignment = new CrewAssignment(crew, command.hoursWorked(), command.workerCount(),
				command.task());
		entry.addAssignment(assignment);
		return assignments.save(assignment).getId();
	}

	@Transactional
	public void update(Long dailyLogId, Long assignmentId, Long expectedLogVersion, CrewAssignmentCommand command) {
		loadLogForChange(dailyLogId, expectedLogVersion);
		CrewAssignment assignment = loadAssignment(dailyLogId, assignmentId);
		boolean crewChanged = !assignment.getCrew().getId().equals(command.crewId());
		if (crewChanged && command.crewId() != null
				&& assignments.existsByWorkEntry_IdAndCrew_Id(assignment.getWorkEntry().getId(), command.crewId())) {
			throw new InvalidFieldException("crewId", DailyLogAssembler.CREW_DUPLICATE);
		}
		assignment.setCrew(assembler.resolveCrew(command.crewId(), assignment.getCrew(), "crewId", new HashMap<>()));
		assignment.setHoursWorked(command.hoursWorked());
		assignment.setWorkerCount(command.workerCount());
		assignment.setTask(command.task());
	}

	/** Removes the row; {@code orphanRemoval} deletes it at flush. */
	@Transactional
	public void delete(Long dailyLogId, Long assignmentId, Long expectedLogVersion) {
		loadLogForChange(dailyLogId, expectedLogVersion);
		CrewAssignment assignment = loadAssignment(dailyLogId, assignmentId);
		assignment.getWorkEntry().removeAssignment(assignment);
	}

	private DailyLog loadLogForChange(Long dailyLogId, Long expectedLogVersion) {
		DailyLog log = dailyLogs.findForChildUpdateById(dailyLogId)
			.orElseThrow(() -> new NotFoundException(DailyLog.class, dailyLogId));
		DailyLogRules.requireVersion(log, expectedLogVersion);
		DailyLogRules.requireEditable(log);
		return log;
	}

	private CrewAssignment loadAssignment(Long dailyLogId, Long assignmentId) {
		return assignments.findDetailByIdAndDailyLogId(assignmentId, dailyLogId)
			.orElseThrow(() -> new NotFoundException(CrewAssignment.class, assignmentId));
	}

}
