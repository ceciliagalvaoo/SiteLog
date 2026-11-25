package hr.algebra.sitelog.audit.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.audit.domain.ActionOutcome;
import hr.algebra.sitelog.audit.domain.AuditedEntityType;
import hr.algebra.sitelog.audit.domain.DomainAction;
import hr.algebra.sitelog.audit.repository.DomainActionRepository;
import hr.algebra.sitelog.user.domain.AppUser;
import hr.algebra.sitelog.user.repository.AppUserRepository;

/**
 * Reads the recorded domain actions of one record (incident timeline, daily log history). Only
 * successful actions are shown; refused and failed attempts stay in the table and the log for
 * whoever investigates. Two queries: the actions, then the names of their actors.
 */
@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class DomainActionService {

	private final DomainActionRepository actions;

	private final AppUserRepository users;

	private final Clock clock;

	public DomainActionService(DomainActionRepository actions, AppUserRepository users, Clock clock) {
		this.actions = actions;
		this.users = users;
		this.clock = clock;
	}

	public List<DomainActionView> timeline(AuditedEntityType entityType, Long entityId) {
		List<DomainAction> rows = actions.findByEntityTypeAndEntityIdAndOutcomeOrderByOccurredAtAscIdAsc(entityType,
				entityId, ActionOutcome.SUCCESS);
		List<Long> actorIds = rows.stream().map(DomainAction::getActorId).filter(Objects::nonNull).distinct().toList();
		Map<Long, String> names = actorIds.isEmpty() ? Map.of()
				: users.findAllById(actorIds).stream()
					.collect(Collectors.toMap(AppUser::getId, AppUser::getFullName, (a, b) -> a));
		Function<DomainAction, String> actorName = row -> row.getActorId() != null
				? names.getOrDefault(row.getActorId(), row.getActorEmail())
				: row.getActorEmail();
		return rows.stream()
			.map(row -> DomainActionView.of(row.getAction(), actorName.apply(row),
					row.getOccurredAt().atZone(clock.getZone())))
			.toList();
	}

}
