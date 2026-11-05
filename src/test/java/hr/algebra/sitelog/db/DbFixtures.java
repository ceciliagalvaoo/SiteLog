package hr.algebra.sitelog.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Plain-SQL fixtures for schema tests (no JPA entities exist yet). Every fixture uses fresh
 * natural keys, so the tests do not collide with the V4 demo data or with each other.
 */
final class DbFixtures {

	static final String MANAGER_EMAIL = "manager@sitelog.test";

	private final JdbcTemplate jdbc;

	DbFixtures(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	long userId(String email) {
		return jdbc.queryForObject("SELECT id FROM app_user WHERE email = ?", Long.class, email);
	}

	long newSite() {
		String code = "T-" + UUID.randomUUID().toString().substring(0, 8);
		return jdbc.queryForObject("""
				INSERT INTO site (code, name, address, start_date, manager_id)
				VALUES (?, 'Test site', 'Test street 1, Zagreb', DATE '2026-01-01', ?)
				RETURNING id""", Long.class, code, userId(MANAGER_EMAIL));
	}

	long newCrew() {
		return jdbc.queryForObject("""
				INSERT INTO crew (name, company, trade) VALUES (?, 'Test d.o.o.', 'Formwork')
				RETURNING id""", Long.class, "Crew " + UUID.randomUUID());
	}

	long newDraftLog(long siteId) {
		return jdbc.queryForObject("""
				INSERT INTO daily_log (site_id, log_date, weather, notes)
				VALUES (?, ?, 'SUNNY', 'Initial notes')
				RETURNING id""", Long.class, siteId, LocalDate.of(2026, 11, 2));
	}

	long newWorkEntry(long dailyLogId) {
		return jdbc.queryForObject("""
				INSERT INTO work_entry (daily_log_id, description, start_time, end_time)
				VALUES (?, 'Formwork', TIME '07:00', TIME '15:00')
				RETURNING id""", Long.class, dailyLogId);
	}

	long newCrewAssignment(long workEntryId, long crewId) {
		return jdbc.queryForObject("""
				INSERT INTO crew_assignment (work_entry_id, crew_id, hours_worked, worker_count, task)
				VALUES (?, ?, 8, 4, 'Slab formwork')
				RETURNING id""", Long.class, workEntryId, crewId);
	}

	/** The sign-off UPDATE as the service will issue it: status, signer, time and version. */
	int signOff(long dailyLogId) {
		return jdbc.update("""
				UPDATE daily_log
				SET status = 'SIGNED_OFF', signed_off_by = ?, signed_off_at = now(), version = version + 1
				WHERE id = ?""", userId(MANAGER_EMAIL), dailyLogId);
	}

	/** The reopen UPDATE as the service will issue it after an approved request. */
	int reopen(long dailyLogId) {
		return jdbc.update("""
				UPDATE daily_log
				SET status = 'REOPENED', revision = revision + 1, version = version + 1
				WHERE id = ?""", dailyLogId);
	}

	String status(long dailyLogId) {
		return jdbc.queryForObject("SELECT status FROM daily_log WHERE id = ?", String.class, dailyLogId);
	}

	/** Asserts that the lock trigger rejected the statement (SQLSTATE P0001, message daily_log_locked). */
	static void assertLocked(ThrowingCallable statement) {
		assertThatThrownBy(statement)
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("daily_log_locked")
			.satisfies(ex -> assertThat(sqlState(ex)).isEqualTo("P0001"));
	}

	/** Asserts that a CHECK constraint with the given name rejected the statement (SQLSTATE 23514). */
	static void assertCheckViolation(ThrowingCallable statement,
			String constraintName) {
		assertThatThrownBy(statement)
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining(constraintName)
			.satisfies(ex -> assertThat(sqlState(ex)).isEqualTo("23514"));
	}

	private static String sqlState(Throwable ex) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (t instanceof SQLException sql) {
				return sql.getSQLState();
			}
		}
		return null;
	}

}
