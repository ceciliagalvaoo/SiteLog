package hr.algebra.sitelog.dailylog.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.audit.domain.AuditedEntityType;
import hr.algebra.sitelog.audit.service.DomainActionService;
import hr.algebra.sitelog.common.audit.AuditRevision;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.dailylog.domain.CrewAssignment;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.WorkEntry;
import hr.algebra.sitelog.dailylog.repository.DailyLogRepository;
import hr.algebra.sitelog.user.domain.AppUser;
import hr.algebra.sitelog.user.repository.AppUserRepository;
import jakarta.persistence.EntityManager;

/**
 * Revision history of a daily log with before/after per field, read with Envers'
 * {@link AuditReader} (ADR-0003 decision 4).
 *
 * <p>How: three audit queries, one per audited entity of the aggregate -
 * <ol>
 * <li>{@code DailyLog} revisions of this id;</li>
 * <li>{@code WorkEntry} revisions of every entry that ever belonged to the log. A DEL row
 * stores only id/rev/revtype (Envers default, no {@code store_data_at_delete}), so entries are
 * first found by {@code relatedId("dailyLog")} and then all their revisions are read by id;</li>
 * <li>{@code CrewAssignment} revisions the same way, through those work entries.</li>
 * </ol>
 * Each row's revisions are walked in order and compared with the previous snapshot of the same
 * row: ADD shows every value as "after", MOD only the fields that differ, DEL the last values as
 * "before". Changes are then grouped by revision (one revision = one committed transaction,
 * with its user and time from {@link AuditRevision}).
 *
 * <p>All revisions are listed, DRAFT edits included (decision 5 of the ADR); the reopen
 * requests and the domain actions are returned next to them so the page can show which edits
 * belong to which reopen.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class DailyLogHistoryService {

	static final String DAILY_LOG = "DAILY_LOG";

	static final String WORK_ENTRY = "WORK_ENTRY";

	static final String CREW_ASSIGNMENT = "CREW_ASSIGNMENT";

	private final EntityManager entityManager;

	private final DailyLogRepository dailyLogs;

	private final AppUserRepository users;

	private final LogReopenService reopenService;

	private final DomainActionService domainActions;

	private final Clock clock;

	public DailyLogHistoryService(EntityManager entityManager, DailyLogRepository dailyLogs, AppUserRepository users,
			LogReopenService reopenService, DomainActionService domainActions, Clock clock) {
		this.entityManager = entityManager;
		this.dailyLogs = dailyLogs;
		this.users = users;
		this.reopenService = reopenService;
		this.domainActions = domainActions;
		this.clock = clock;
	}

	public DailyLogHistory history(Long dailyLogId) {
		DailyLog log = dailyLogs.findWithSiteById(dailyLogId)
			.orElseThrow(() -> new NotFoundException(DailyLog.class, dailyLogId));
		return new DailyLogHistory(DailyLogHeader.of(log), log.getRevision(), revisions(dailyLogId),
				reopenService.history(dailyLogId), domainActions.timeline(AuditedEntityType.DAILY_LOG, dailyLogId));
	}

	/** Envers revisions of the log and its children, newest first. */
	public List<RevisionView> revisions(Long dailyLogId) {
		AuditReader reader = AuditReaderFactory.get(entityManager);
		ZoneId zone = clock.getZone();
		Map<Long, AuditRevision> revisionInfo = new HashMap<>();
		Map<Long, List<EntityChange>> changesByRevision = new TreeMap<>(Comparator.reverseOrder());

		List<Object[]> logRows = query(reader, DailyLog.class, AuditEntity.id().eq(dailyLogId));
		collect(logRows, DAILY_LOG, this::logSnapshot, row -> null, revisionInfo,
				changesByRevision);

		Set<Long> entryIds = idsOf(query(reader, WorkEntry.class, AuditEntity.relatedId("dailyLog").eq(dailyLogId)));
		if (!entryIds.isEmpty()) {
			List<Object[]> entryRows = query(reader, WorkEntry.class, AuditEntity.id().in(entryIds));
			collect(entryRows, WORK_ENTRY, DailyLogHistoryService::entrySnapshot, WorkEntry::getDescription,
					revisionInfo, changesByRevision);

			Set<Long> assignmentIds = idsOf(
					query(reader, CrewAssignment.class, AuditEntity.relatedId("workEntry").in(entryIds.toArray())));
			if (!assignmentIds.isEmpty()) {
				List<Object[]> assignmentRows = query(reader, CrewAssignment.class,
						AuditEntity.id().in(assignmentIds));
				collect(assignmentRows, CREW_ASSIGNMENT, DailyLogHistoryService::assignmentSnapshot,
						DailyLogHistoryService::crewName, revisionInfo, changesByRevision);
			}
		}

		Map<String, String> names = namesByEmail(revisionInfo.values());
		List<RevisionView> result = new ArrayList<>();
		changesByRevision.forEach((rev, changes) -> {
			AuditRevision info = revisionInfo.get(rev);
			result.add(new RevisionView(rev, Instant.ofEpochMilli(info.getTimestamp()).atZone(zone),
					info.getUsername(), names.get(info.getUsername()), List.copyOf(changes)));
		});
		return result;
	}

	@SuppressWarnings("unchecked")
	private static List<Object[]> query(AuditReader reader, Class<?> type,
			org.hibernate.envers.query.criteria.AuditCriterion criterion) {
		return reader.createQuery()
			.forRevisionsOfEntity(type, false, true)
			.add(criterion)
			.addOrder(AuditEntity.revisionNumber().asc())
			.getResultList();
	}

	private static Set<Long> idsOf(List<Object[]> rows) {
		Set<Long> ids = new LinkedHashSet<>();
		rows.forEach(row -> ids.add(((hr.algebra.sitelog.common.domain.BaseEntity) row[0]).getId()));
		return ids;
	}

	/**
	 * Walks the rows (already in revision order) per entity id and turns each into an
	 * {@link EntityChange} against the previous snapshot of the same id.
	 */
	@SuppressWarnings("unchecked")
	private <T extends hr.algebra.sitelog.common.domain.BaseEntity> void collect(List<Object[]> rows, String entity,
			Function<T, Map<String, Snapshot>> snapshot, Function<T, String> label, Map<Long, AuditRevision> info,
			Map<Long, List<EntityChange>> changesByRevision) {
		Map<Long, Map<String, Snapshot>> last = new HashMap<>();
		Map<Long, String> lastLabel = new HashMap<>();
		for (Object[] row : rows) {
			T state = (T) row[0];
			AuditRevision revision = (AuditRevision) row[1];
			RevisionType type = (RevisionType) row[2];
			info.putIfAbsent(revision.getRev(), revision);
			Long id = state.getId();
			Map<String, Snapshot> before = last.get(id);
			EntityChange change;
			if (type == RevisionType.DEL) {
				change = new EntityChange(entity, id, "DELETED", lastLabel.get(id), diff(before, null));
				last.remove(id);
			}
			else {
				Map<String, Snapshot> after = snapshot.apply(state);
				String name = label.apply(state);
				lastLabel.put(id, name);
				boolean added = type == RevisionType.ADD || before == null;
				change = new EntityChange(entity, id, added ? "ADDED" : "MODIFIED", name,
						diff(added ? null : before, after));
				last.put(id, after);
			}
			if (!change.fields().isEmpty() || !"MODIFIED".equals(change.changeType())) {
				changesByRevision.computeIfAbsent(revision.getRev(), r -> new ArrayList<>()).add(change);
			}
		}
	}

	/** Fields whose value differs; a missing side is null (added / deleted row). */
	static List<FieldChange> diff(Map<String, Snapshot> before, Map<String, Snapshot> after) {
		Map<String, Snapshot> keys = new LinkedHashMap<>();
		if (before != null) {
			keys.putAll(before);
		}
		if (after != null) {
			keys.putAll(after);
		}
		List<FieldChange> changes = new ArrayList<>();
		keys.forEach((labelKey, any) -> {
			Object old = before == null || before.get(labelKey) == null ? null : before.get(labelKey).value();
			Object now = after == null || after.get(labelKey) == null ? null : after.get(labelKey).value();
			if (!sameValue(old, now)) {
				changes.add(new FieldChange(labelKey, old, now, any.valueKeyPrefix()));
			}
		});
		return changes;
	}

	/** {@code BigDecimal} 8.0 and 8.00 are the same number of hours. */
	private static boolean sameValue(Object a, Object b) {
		if (a instanceof java.math.BigDecimal x && b instanceof java.math.BigDecimal y) {
			return x.compareTo(y) == 0;
		}
		return Objects.equals(a, b);
	}

	/** A field value of one revision, with the message prefix of its enum values (or null). */
	record Snapshot(Object value, String valueKeyPrefix) {

		static Snapshot of(Object value) {
			return new Snapshot(value, null);
		}

		static Snapshot ofEnum(Enum<?> value, String prefix) {
			return new Snapshot(value == null ? null : value.name(), prefix);
		}

	}

	private Map<String, Snapshot> logSnapshot(DailyLog log) {
		Map<String, Snapshot> values = new LinkedHashMap<>();
		values.put("history.field.logDate", Snapshot.of(log.getLogDate()));
		values.put("history.field.status", Snapshot.ofEnum(log.getStatus(), "status.log."));
		values.put("history.field.revision", Snapshot.of(log.getRevision()));
		values.put("history.field.weather", Snapshot.ofEnum(log.getWeather(), "weather."));
		values.put("history.field.notes", Snapshot.of(log.getNotes()));
		values.put("history.field.signedOffBy",
				Snapshot.of(log.getSignedOffBy() == null ? null : log.getSignedOffBy().getFullName()));
		values.put("history.field.signedOffAt", Snapshot.of(log.getSignedOffAt() == null ? null
				: log.getSignedOffAt().atZone(clock.getZone()).toLocalDateTime().withSecond(0).withNano(0)));
		return values;
	}

	private static Map<String, Snapshot> entrySnapshot(WorkEntry entry) {
		Map<String, Snapshot> values = new LinkedHashMap<>();
		values.put("history.field.description", Snapshot.of(entry.getDescription()));
		values.put("history.field.location", Snapshot.of(entry.getLocation()));
		values.put("history.field.startTime", Snapshot.of(entry.getStartTime()));
		values.put("history.field.endTime", Snapshot.of(entry.getEndTime()));
		return values;
	}

	private static Map<String, Snapshot> assignmentSnapshot(CrewAssignment assignment) {
		Map<String, Snapshot> values = new LinkedHashMap<>();
		values.put("history.field.crew", Snapshot.of(crewName(assignment)));
		values.put("history.field.hoursWorked", Snapshot.of(assignment.getHoursWorked()));
		values.put("history.field.workerCount", Snapshot.of(assignment.getWorkerCount()));
		values.put("history.field.task", Snapshot.of(assignment.getTask()));
		return values;
	}

	/** The crew is NOT_AUDITED: Envers returns the current crew row, its name is today's name. */
	private static String crewName(CrewAssignment assignment) {
		return assignment.getCrew() == null ? null : assignment.getCrew().getName();
	}

	private Map<String, String> namesByEmail(java.util.Collection<AuditRevision> revisions) {
		Set<String> emails = revisions.stream().map(AuditRevision::getUsername).filter(Objects::nonNull)
			.collect(Collectors.toSet());
		if (emails.isEmpty()) {
			return Map.of();
		}
		return users.findByEmailIn(emails).stream()
			.collect(Collectors.toMap(AppUser::getEmail, AppUser::getFullName, (a, b) -> a));
	}

}
