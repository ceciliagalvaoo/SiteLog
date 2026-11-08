package hr.algebra.sitelog.dailylog.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import hr.algebra.sitelog.dailylog.domain.LogReopenRequest;
import hr.algebra.sitelog.dailylog.domain.ReopenStatus;

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

	boolean existsByDailyLog_IdAndStatus(Long dailyLogId, ReopenStatus status);

	/** Delete rule for DRAFT logs: a log with reopen history was signed off once and is never deleted. */
	boolean existsByDailyLog_Id(Long dailyLogId);

}
