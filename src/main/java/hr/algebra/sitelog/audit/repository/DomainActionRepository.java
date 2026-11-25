package hr.algebra.sitelog.audit.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import hr.algebra.sitelog.audit.domain.ActionOutcome;
import hr.algebra.sitelog.audit.domain.AuditedEntityType;
import hr.algebra.sitelog.audit.domain.DomainAction;

/** Only inserts ({@code save}) and the timeline read; the table is append-only. */
public interface DomainActionRepository extends JpaRepository<DomainAction, Long> {

	/** Timeline of one record, oldest first (index {@code ix_domain_action_entity}). */
	List<DomainAction> findByEntityTypeAndEntityIdAndOutcomeOrderByOccurredAtAscIdAsc(AuditedEntityType entityType,
			Long entityId, ActionOutcome outcome);

}
