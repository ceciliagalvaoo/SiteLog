package hr.algebra.sitelog.dailylog.service;

import static hr.algebra.sitelog.support.TestEntities.TODAY;
import static hr.algebra.sitelog.support.TestEntities.log;
import static hr.algebra.sitelog.support.TestEntities.manager;
import static hr.algebra.sitelog.support.TestEntities.site;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import hr.algebra.sitelog.common.exception.LogLockedException;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.dailylog.domain.LogStatus;

class DailyLogRulesTests {

	private static DailyLog logIn(LogStatus status) {
		return log(10L, site(1L, "ZG-1", manager(7L)), TODAY, status, 3L);
	}

	@Test
	void requireVersion_whenFormVersionMatches_passes() {
		assertThatCode(() -> DailyLogRules.requireVersion(logIn(LogStatus.DRAFT), 3L)).doesNotThrowAnyException();
	}

	@Test
	void requireVersion_whenFormVersionIsStale_throwsOptimisticLockingFailureForDailyLog() {
		assertThatThrownBy(() -> DailyLogRules.requireVersion(logIn(LogStatus.DRAFT), 2L))
			.isInstanceOfSatisfying(ObjectOptimisticLockingFailureException.class, e -> {
				assertThat(e.getPersistentClass()).isEqualTo(DailyLog.class);
				assertThat(e.getIdentifier()).isEqualTo(10L);
			});
	}

	@Test
	void requireVersion_whenFormSentNoVersion_throwsOptimisticLockingFailure() {
		assertThatThrownBy(() -> DailyLogRules.requireVersion(logIn(LogStatus.DRAFT), null))
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

	@ParameterizedTest
	@EnumSource(value = LogStatus.class, names = { "DRAFT", "REOPENED" })
	void requireEditable_whenDraftOrReopened_passes(LogStatus status) {
		assertThatCode(() -> DailyLogRules.requireEditable(logIn(status))).doesNotThrowAnyException();
	}

	@Test
	void requireEditable_whenSignedOff_throwsLogLockedExceptionWithLogId() {
		assertThatThrownBy(() -> DailyLogRules.requireEditable(logIn(LogStatus.SIGNED_OFF)))
			.isInstanceOfSatisfying(LogLockedException.class, e -> {
				assertThat(e.getDailyLogId()).isEqualTo(10L);
				assertThat(e.getMessageKey()).isEqualTo("dailyLog.flash.locked");
			});
	}

}
