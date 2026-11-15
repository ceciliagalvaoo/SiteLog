package hr.algebra.sitelog.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Recognition of the V3 lock trigger error ({@code P0001} + {@code daily_log_locked}). */
class WebExceptionHandlerTests {

	private static DataIntegrityViolationException wrapping(SQLException sql) {
		// same nesting as Spring -> Hibernate JDBCException -> PSQLException
		return new DataIntegrityViolationException("could not execute statement",
				new RuntimeException("could not execute statement", sql));
	}

	@Test
	void lockTrigger_isRecognisedBySqlStateAndMessageDeepInTheCauseChain() {
		assertThat(WebExceptionHandler.isLockTrigger(
				wrapping(new SQLException("ERROR: daily_log_locked\n  Where: PL/pgSQL function", "P0001"))))
			.isTrue();
	}

	@Test
	void otherRaiseException_withTheSameSqlState_isNotTheLock() {
		assertThat(WebExceptionHandler.isLockTrigger(wrapping(new SQLException("ERROR: audit_append_only", "P0001"))))
			.isFalse();
	}

	@Test
	void lockMessage_withoutTheTriggerSqlState_isNotTheLock() {
		assertThat(WebExceptionHandler.isLockTrigger(wrapping(new SQLException("ERROR: daily_log_locked", "23514"))))
			.isFalse();
		assertThat(WebExceptionHandler.isLockTrigger(wrapping(new SQLException("ERROR: daily_log_locked")))).isFalse();
	}

}
