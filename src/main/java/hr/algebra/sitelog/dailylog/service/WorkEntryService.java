package hr.algebra.sitelog.dailylog.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.WorkEntry;
import hr.algebra.sitelog.dailylog.repository.DailyLogRepository;
import hr.algebra.sitelog.dailylog.repository.WorkEntryRepository;

/**
 * One work entry of a log at a time (work entry page, and the REST endpoints in M3).
 *
 * <p>Every write loads the parent log with {@code findForChildUpdateById}
 * ({@code OPTIMISTIC_FORCE_INCREMENT}): the change touches only {@code work_entry} /
 * {@code crew_assignment}, but {@code daily_log.version} still goes up, so a concurrent edit of
 * the same log - on any child - conflicts (ADR-0001 item 6). Then the version the user saw and
 * the lock after sign-off are checked, as for the log itself.
 */
@Service
@Transactional(readOnly = true)
public class WorkEntryService {

	private final DailyLogRepository dailyLogs;

	private final WorkEntryRepository workEntries;

	private final DailyLogAssembler assembler;

	public WorkEntryService(DailyLogRepository dailyLogs, WorkEntryRepository workEntries,
			DailyLogAssembler assembler) {
		this.dailyLogs = dailyLogs;
		this.workEntries = workEntries;
		this.assembler = assembler;
	}

	public WorkEntryView get(Long dailyLogId, Long workEntryId) {
		return WorkEntryView.of(loadEntry(dailyLogId, workEntryId));
	}

	@Transactional
	public Long create(Long dailyLogId, Long expectedLogVersion, WorkEntryCommand command) {
		DailyLog log = loadLogForChange(dailyLogId, expectedLogVersion);
		WorkEntry entry = new WorkEntry(command.description());
		assembler.apply(entry, command, "");
		log.addWorkEntry(entry);
		return workEntries.save(entry).getId();
	}

	@Transactional
	public void update(Long dailyLogId, Long workEntryId, Long expectedLogVersion, WorkEntryCommand command) {
		loadLogForChange(dailyLogId, expectedLogVersion);
		assembler.apply(loadEntry(dailyLogId, workEntryId), command, "");
	}

	/** Removes the entry; {@code orphanRemoval} deletes it and its crew rows at flush. */
	@Transactional
	public void delete(Long dailyLogId, Long workEntryId, Long expectedLogVersion) {
		DailyLog log = loadLogForChange(dailyLogId, expectedLogVersion);
		log.removeWorkEntry(loadEntry(dailyLogId, workEntryId));
	}

	private DailyLog loadLogForChange(Long dailyLogId, Long expectedLogVersion) {
		DailyLog log = dailyLogs.findForChildUpdateById(dailyLogId)
			.orElseThrow(() -> new NotFoundException(DailyLog.class, dailyLogId));
		DailyLogRules.requireVersion(log, expectedLogVersion);
		DailyLogRules.requireEditable(log);
		return log;
	}

	/** The entry with its crew rows, only if it belongs to the log in the URL. */
	private WorkEntry loadEntry(Long dailyLogId, Long workEntryId) {
		return workEntries.findDetailById(workEntryId)
			.filter(entry -> entry.getDailyLog().getId().equals(dailyLogId))
			.orElseThrow(() -> new NotFoundException(WorkEntry.class, workEntryId));
	}

}
