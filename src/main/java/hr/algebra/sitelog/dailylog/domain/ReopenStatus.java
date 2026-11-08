package hr.algebra.sitelog.dailylog.domain;

/** Decision state of a reopen request. Values match {@code ck_log_reopen_request_status} in V3. */
public enum ReopenStatus {
	PENDING,
	APPROVED,
	REJECTED
}
