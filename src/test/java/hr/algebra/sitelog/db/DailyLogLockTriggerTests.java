package hr.algebra.sitelog.db;

import static hr.algebra.sitelog.db.DbFixtures.assertLocked;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.TestcontainersConfiguration;

/**
 * Verifies the V3 lock triggers on a real PostgreSQL: a SIGNED_OFF daily log and its
 * work entries and crew assignments cannot change, except for the reopen transition.
 * Each test runs in a transaction that is rolled back.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DailyLogLockTriggerTests {

	private final JdbcTemplate jdbc;
	private final DbFixtures fixtures;

	private long crewId;
	private long logId;
	private long workEntryId;
	private long assignmentId;

	@Autowired
	DailyLogLockTriggerTests(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
		this.fixtures = new DbFixtures(jdbc);
	}

	@BeforeEach
	void createDraftLogWithChildren() {
		crewId = fixtures.newCrew();
		logId = fixtures.newDraftLog(fixtures.newSite());
		workEntryId = fixtures.newWorkEntry(logId);
		assignmentId = fixtures.newCrewAssignment(workEntryId, crewId);
	}

	@Nested
	class WhenSignedOff {

		@BeforeEach
		void signOff() {
			assertThat(fixtures.signOff(logId)).isEqualTo(1);
		}

		@Test
		void updatingWorkEntryIsRejected() {
			assertLocked(() -> jdbc.update(
					"UPDATE work_entry SET description = 'Changed' WHERE id = ?", workEntryId));
		}

		@Test
		void insertingWorkEntryIsRejected() {
			assertLocked(() -> fixtures.newWorkEntry(logId));
		}

		@Test
		void deletingWorkEntryIsRejected() {
			assertLocked(() -> jdbc.update("DELETE FROM work_entry WHERE id = ?", workEntryId));
		}

		@Test
		void insertingCrewAssignmentIsRejected() {
			assertLocked(() -> fixtures.newCrewAssignment(workEntryId, fixtures.newCrew()));
		}

		@Test
		void updatingCrewAssignmentIsRejected() {
			assertLocked(() -> jdbc.update(
					"UPDATE crew_assignment SET hours_worked = 6 WHERE id = ?", assignmentId));
		}

		@Test
		void deletingCrewAssignmentIsRejected() {
			assertLocked(() -> jdbc.update("DELETE FROM crew_assignment WHERE id = ?", assignmentId));
		}

		@Test
		void editingLogContentIsRejected() {
			assertLocked(() -> jdbc.update("UPDATE daily_log SET notes = 'Changed' WHERE id = ?", logId));
		}

		@Test
		void versionOnlyIncrementIsRejected() {
			// what OPTIMISTIC_FORCE_INCREMENT would issue if a child had been changed
			assertLocked(() -> jdbc.update("UPDATE daily_log SET version = version + 1 WHERE id = ?", logId));
		}

		@Test
		void reopenWithoutRevisionIncrementIsRejected() {
			assertLocked(() -> jdbc.update("UPDATE daily_log SET status = 'REOPENED' WHERE id = ?", logId));
		}

		@Test
		void reopenThatAlsoEditsContentIsRejected() {
			assertLocked(() -> jdbc.update("""
					UPDATE daily_log SET status = 'REOPENED', revision = revision + 1, weather = 'RAIN'
					WHERE id = ?""", logId));
		}

		@Test
		void backToDraftIsRejected() {
			assertLocked(() -> jdbc.update("UPDATE daily_log SET status = 'DRAFT' WHERE id = ?", logId));
		}

		@Test
		void deletingLogIsRejected() {
			assertLocked(() -> jdbc.update("DELETE FROM daily_log WHERE id = ?", logId));
		}

		@Test
		void reopenIsAllowedAndUnlocksChildren() {
			assertThat(fixtures.reopen(logId)).isEqualTo(1);

			assertThat(fixtures.status(logId)).isEqualTo("REOPENED");
			assertThat(jdbc.queryForObject("SELECT revision FROM daily_log WHERE id = ?", Integer.class, logId))
				.isEqualTo(1);
			assertThat(jdbc.update("UPDATE crew_assignment SET hours_worked = 6 WHERE id = ?", assignmentId))
				.isEqualTo(1);
		}

		@Test
		void signOffAgainAfterReopenIsAllowedAndLocksAgain() {
			fixtures.reopen(logId);
			fixtures.newWorkEntry(logId);

			assertThat(fixtures.signOff(logId)).isEqualTo(1);

			assertThat(fixtures.status(logId)).isEqualTo("SIGNED_OFF");
			assertLocked(() -> fixtures.newWorkEntry(logId));
		}

	}

	@Test
	void draftLogAndChildrenAreEditable() {
		assertThat(jdbc.update("UPDATE daily_log SET notes = 'Edited' WHERE id = ?", logId)).isEqualTo(1);
		assertThat(jdbc.update("UPDATE work_entry SET location = 'Level 2' WHERE id = ?", workEntryId))
			.isEqualTo(1);
		assertThat(jdbc.update("UPDATE crew_assignment SET worker_count = 5 WHERE id = ?", assignmentId))
			.isEqualTo(1);
		long secondEntry = fixtures.newWorkEntry(logId);
		fixtures.newCrewAssignment(secondEntry, crewId);

		assertThat(jdbc.update("DELETE FROM crew_assignment WHERE id = ?", assignmentId)).isEqualTo(1);
		assertThat(jdbc.update("DELETE FROM work_entry WHERE id = ?", workEntryId)).isEqualTo(1);
	}

	@Test
	void signOffOfDraftLogIsAllowed() {
		assertThat(fixtures.signOff(logId)).isEqualTo(1);

		assertThat(fixtures.status(logId)).isEqualTo("SIGNED_OFF");
	}

	@Test
	void draftLogWithChildrenRemovedCanBeDeleted() {
		jdbc.update("DELETE FROM crew_assignment WHERE id = ?", assignmentId);
		jdbc.update("DELETE FROM work_entry WHERE id = ?", workEntryId);

		assertThat(jdbc.update("DELETE FROM daily_log WHERE id = ?", logId)).isEqualTo(1);
	}

	@Test
	void movingWorkEntryIntoSignedOffLogIsRejected() {
		long otherLog = jdbc.queryForObject("""
				INSERT INTO daily_log (site_id, log_date) VALUES (?, DATE '2026-11-03') RETURNING id""",
				Long.class, fixtures.newSite());
		fixtures.signOff(otherLog);

		assertLocked(() -> jdbc.update(
				"UPDATE work_entry SET daily_log_id = ? WHERE id = ?", otherLog, workEntryId));
	}

}
