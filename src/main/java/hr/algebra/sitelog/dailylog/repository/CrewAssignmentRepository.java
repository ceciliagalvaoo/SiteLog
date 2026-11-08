package hr.algebra.sitelog.dailylog.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import hr.algebra.sitelog.dailylog.domain.CrewAssignment;

public interface CrewAssignmentRepository extends JpaRepository<CrewAssignment, Long> {

	/**
	 * Crew assignment form / delete: the row, its crew and its work entry, only if it belongs to
	 * the given daily log (nested URL {@code /daily-logs/{logId}/crew-assignments/{id}}).
	 */
	@Query("""
			select a from CrewAssignment a
			join fetch a.crew
			join fetch a.workEntry w
			where a.id = :id and w.dailyLog.id = :dailyLogId""")
	Optional<CrewAssignment> findDetailByIdAndDailyLogId(@Param("id") Long id, @Param("dailyLogId") Long dailyLogId);

	/** Delete rule for crews: a crew with assignments is deactivated, not deleted. */
	boolean existsByCrew_Id(Long crewId);

	/** Validation before adding a row ({@code uk_crew_assignment_entry_crew}). */
	boolean existsByWorkEntry_IdAndCrew_Id(Long workEntryId, Long crewId);

}
