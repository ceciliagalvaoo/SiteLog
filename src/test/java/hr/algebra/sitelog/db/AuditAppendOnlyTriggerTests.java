package hr.algebra.sitelog.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.TestcontainersConfiguration;

/**
 * Verifies the V5 triggers on a real PostgreSQL: the Envers audit tables accept INSERT but
 * reject UPDATE, DELETE and TRUNCATE. Each test runs in a transaction that is rolled back,
 * and every rejected statement is the last one of its test (PostgreSQL aborts the
 * transaction after an error).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AuditAppendOnlyTriggerTests {

	private final JdbcTemplate jdbc;

	private long rev;

	@Autowired
	AuditAppendOnlyTriggerTests(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** One revision with one row in each audit table, written the way Envers writes them. */
	@BeforeEach
	void insertRevisionWithAuditRows() {
		rev = jdbc.queryForObject("""
				INSERT INTO revinfo (revtstmp, username) VALUES (?, 'manager@sitelog.test')
				RETURNING rev""", Long.class, System.currentTimeMillis());
		jdbc.update("""
				INSERT INTO daily_log_aud (id, rev, revtype, site_id, log_date, status, revision)
				VALUES (900001, ?, 0, 1, DATE '2026-11-02', 'DRAFT', 0)""", rev);
		jdbc.update("""
				INSERT INTO work_entry_aud (id, rev, revtype, daily_log_id, description)
				VALUES (900001, ?, 0, 900001, 'Formwork')""", rev);
		jdbc.update("""
				INSERT INTO crew_assignment_aud (id, rev, revtype, work_entry_id, crew_id, hours_worked)
				VALUES (900001, ?, 0, 900001, 1, 8)""", rev);
	}

	@Test
	void insertIsAllowed() {
		long nextRev = jdbc.queryForObject("""
				INSERT INTO revinfo (revtstmp, username) VALUES (?, 'supervisor@sitelog.test')
				RETURNING rev""", Long.class, System.currentTimeMillis());

		// a later revision of the same log (MOD), as written after a reopen
		assertThat(jdbc.update("""
				INSERT INTO daily_log_aud (id, rev, revtype, status, revision)
				VALUES (900001, ?, 1, 'REOPENED', 1)""", nextRev)).isEqualTo(1);
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM daily_log_aud WHERE id = 900001", Integer.class)).isEqualTo(2);
	}

	@ParameterizedTest
	@ValueSource(strings = { "daily_log_aud", "work_entry_aud", "crew_assignment_aud" })
	void updatingAuditRowIsRejected(String table) {
		assertAppendOnly(() -> jdbc.update("UPDATE " + table + " SET revtype = 2 WHERE rev = ?", rev));
	}

	@ParameterizedTest
	@ValueSource(strings = { "daily_log_aud", "work_entry_aud", "crew_assignment_aud" })
	void deletingAuditRowIsRejected(String table) {
		assertAppendOnly(() -> jdbc.update("DELETE FROM " + table + " WHERE rev = ?", rev));
	}

	@ParameterizedTest
	@ValueSource(strings = { "daily_log_aud", "work_entry_aud", "crew_assignment_aud" })
	void truncatingAuditTableIsRejected(String table) {
		assertAppendOnly(() -> jdbc.execute("TRUNCATE " + table));
	}

	@Test
	void rewritingRevisionAuthorIsRejected() {
		assertAppendOnly(() -> jdbc.update("UPDATE revinfo SET username = 'someone@else.test' WHERE rev = ?", rev));
	}

	@Test
	void deletingRevisionIsRejected() {
		// the trigger fires before the foreign key from the *_aud rows is checked
		assertAppendOnly(() -> jdbc.update("DELETE FROM revinfo WHERE rev = ?", rev));
	}

	@Test
	void truncatingRevisionsWithCascadeIsRejected() {
		// plain TRUNCATE revinfo is already refused by the foreign keys (SQLSTATE 0A000);
		// CASCADE gets past that check and is stopped by the statement trigger
		assertAppendOnly(() -> jdbc.execute("TRUNCATE revinfo CASCADE"));
	}

	/** Asserts that the V5 trigger rejected the statement (SQLSTATE P0001, message audit_append_only). */
	private static void assertAppendOnly(ThrowingCallable statement) {
		assertThatThrownBy(statement)
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("audit_append_only")
			.satisfies(ex -> assertThat(sqlState(ex)).isEqualTo("P0001"));
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
