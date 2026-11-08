package hr.algebra.sitelog.site.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import hr.algebra.sitelog.site.domain.Site;

public interface SiteRepository extends JpaRepository<Site, Long> {

	/** Site list (screen and {@code GET /api/v1/sites}): the manager's name is shown on each row. */
	@EntityGraph(attributePaths = "manager")
	@Query("select s from Site s")
	Page<Site> findAllWithManager(Pageable pageable);

	/**
	 * Filtered site list: case-insensitive match on code or name. An empty filter is handled by
	 * the service with {@link #findAllWithManager(Pageable)}.
	 */
	@EntityGraph(attributePaths = "manager")
	@Query("""
			select s from Site s
			where lower(s.code) like lower(concat('%', :text, '%'))
			   or lower(s.name) like lower(concat('%', :text, '%'))""")
	Page<Site> searchWithManager(@Param("text") String text, Pageable pageable);

	/** Sites of one manager (SITE_MANAGER sees and edits only these). */
	@EntityGraph(attributePaths = "manager")
	List<Site> findByManager_IdOrderByNameAsc(Long managerId);

	/** Site detail page. */
	@EntityGraph(attributePaths = "manager")
	@Query("select s from Site s where s.id = :id")
	Optional<Site> findWithManagerById(@Param("id") Long id);

	boolean existsByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

	/**
	 * Delete rule for sites: AI incident drafts also reference the site ({@code fk_incident_draft_site}).
	 * Native SQL because {@code IncidentDraft} belongs to the {@code ai} feature and is not mapped
	 * here; the site feature only needs to know whether such a row exists.
	 */
	@Query(value = "select exists (select 1 from incident_draft d where d.site_id = :siteId)", nativeQuery = true)
	boolean existsIncidentDraftBySiteId(@Param("siteId") Long siteId);

	/** Used by the "own site" authorization check without loading the site. */
	boolean existsByIdAndManager_Id(Long siteId, Long managerId);

}
