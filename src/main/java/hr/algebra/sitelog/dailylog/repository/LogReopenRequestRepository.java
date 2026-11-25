package hr.algebra.sitelog.dailylog.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import hr.algebra.sitelog.dailylog.domain.LogReopenRequest;
import hr.algebra.sitelog.dailylog.domain.ReopenStatus;
import jakarta.persistence.LockModeType;

public interface LogReopenRequestRepository extends JpaRepository<LogReopenRequest, Long> {

	/** Reopen history of a log (detail page), newest first, with the people involved. */
	@EntityGraph(attributePaths = { "requestedBy", "approvedBy" })
	List<LogReopenRequest> findByDailyLog_IdOrderByRequestedAtDesc(Long dailyLogId);

	/** The open request of a log, if any (at most one, {@code uk_log_reopen_request_pending}). */
	@EntityGraph(attributePaths = { "dailyLog", "requestedBy" })
	Optional<LogReopenRequest> findByDailyLog_IdAndStatus(Long dailyLogId, ReopenStatus status);

	/** Supervisor queue of pending requests, oldest first. */
	@EntityGraph(attributePaths = { "dailyLog", "dailyLog.site", "requestedBy" })
	List<LogReopenRequest> findByStatusOrderByRequestedAtAsc(ReopenStatus status);

	/**
	 * Loads a request for a decision with {@code SELECT ... FOR UPDATE}: two supervisors deciding
	 * the same request at the same moment are serialised - the second waits, then sees that the
	 * request is no longer PENDING and gets a 409. (An approval alone would also be caught by
	 * {@code daily_log.version}, but a rejection does not change the log, so the row lock is what
	 * stops an approve racing a reject.) No join fetch: the lock covers only this row.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from LogReopenRequest r where r.id = :id")
	Optional<LogReopenRequest> findForDecisionById(@Param("id") Long id);

	boolean existsByDailyLog_IdAndStatus(Long dailyLogId, ReopenStatus status);

	/** Delete rule for DRAFT logs: a log with reopen history was signed off once and is never deleted. */
	boolean existsByDailyLog_Id(Long dailyLogId);

}
