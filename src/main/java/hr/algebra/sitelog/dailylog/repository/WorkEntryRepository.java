package hr.algebra.sitelog.dailylog.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import hr.algebra.sitelog.dailylog.domain.WorkEntry;

public interface WorkEntryRepository extends JpaRepository<WorkEntry, Long> {

	/** Work entry edit page / REST: the entry, its log, its assignments and their crews in one query. */
	@Query("""
			select distinct w from WorkEntry w
			join fetch w.dailyLog
			left join fetch w.assignments a
			left join fetch a.crew
			where w.id = :id""")
	Optional<WorkEntry> findDetailById(@Param("id") Long id);

	/** Ownership check for nested URLs ({@code /daily-logs/{logId}/work-entries/{id}}). */
	boolean existsByIdAndDailyLog_Id(Long id, Long dailyLogId);

}
