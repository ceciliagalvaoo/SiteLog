package hr.algebra.sitelog.incident.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import hr.algebra.sitelog.incident.domain.Incident;
import hr.algebra.sitelog.incident.domain.IncidentStatus;
import jakarta.persistence.LockModeType;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

	/** Incident list of a site with the to-one data shown on each row. */
	@EntityGraph(attributePaths = { "site", "reportedBy" })
	Page<Incident> findBySite_Id(Long siteId, Pageable pageable);

	/** Incident list across sites ({@code GET /api/v1/incidents}). */
	@EntityGraph(attributePaths = { "site", "reportedBy" })
	@Query("select i from Incident i")
	Page<Incident> findAllWithSite(Pageable pageable);

	/**
	 * Incident detail: site, optional daily log, reporter, signer, affected crews (Set) and
	 * corrective actions (Set) with their optional assignee, in one SQL statement.
	 */
	@Query("""
			select distinct i from Incident i
			join fetch i.site
			left join fetch i.dailyLog
			join fetch i.reportedBy
			left join fetch i.signedOffBy
			left join fetch i.affectedCrews
			left join fetch i.correctiveActions ca
			left join fetch ca.assignee
			where i.id = :id""")
	Optional<Incident> findDetailById(@Param("id") Long id);

	/** Same rule as for daily logs: a change to a corrective action bumps the incident's version. */
	@Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
	@Query("select i from Incident i where i.id = :id")
	Optional<Incident> findForChildUpdateById(@Param("id") Long id);

	// ---- delete rules ----

	/** A site with incidents cannot be deleted. */
	boolean existsBySite_Id(Long siteId);

	/** A DRAFT daily log referenced by an incident cannot be deleted ({@code fk_incident_daily_log}). */
	boolean existsByDailyLog_Id(Long dailyLogId);

	/** A crew listed as affected by an incident is deactivated, not deleted. */
	boolean existsByAffectedCrews_Id(Long crewId);

	// ---- site KPIs ----

	long countBySite_Id(Long siteId);

	/** Badge of the "Incidents" sidebar link: open incidents on all sites. */
	long countByStatusIn(Collection<IncidentStatus> statuses);

	long countBySite_IdAndStatusIn(Long siteId, Collection<IncidentStatus> statuses);

}
