package hr.algebra.sitelog.incident.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import hr.algebra.sitelog.incident.domain.CorrectiveAction;
import hr.algebra.sitelog.incident.domain.CorrectiveActionStatus;

public interface CorrectiveActionRepository extends JpaRepository<CorrectiveAction, Long> {

	/** Site KPI: corrective actions still to do (OPEN or OVERDUE) on a site's incidents. */
	long countByIncident_Site_IdAndStatusIn(Long siteId, Collection<CorrectiveActionStatus> statuses);

	/**
	 * Actions past their due date in a given status (job "corrective actions -> OVERDUE",
	 * milestone M4); incident, site and assignee are fetched for the notification text and recipient.
	 */
	@EntityGraph(attributePaths = { "incident", "incident.site", "assignee" })
	List<CorrectiveAction> findByStatusAndDueDateBefore(CorrectiveActionStatus status, LocalDate date);

}
