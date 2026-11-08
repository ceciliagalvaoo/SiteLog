package hr.algebra.sitelog.dailylog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import hr.algebra.sitelog.dailylog.domain.LogReopenRequest;
import hr.algebra.sitelog.dailylog.domain.ReopenStatus;
import hr.algebra.sitelog.support.JpaFixtures;
import hr.algebra.sitelog.support.RepositoryTest;
import jakarta.persistence.EntityManager;

/**
 * {@link LogReopenRequestRepository} on the V4 seed: ZG-01 28/10 APPROVED, ZG-02 27/10 REJECTED,
 * ST-01 27/10 PENDING.
 */
@RepositoryTest
class LogReopenRequestRepositoryTests {

	private final LogReopenRequestRepository requests;
	private final JpaFixtures fixtures;

	@Autowired
	LogReopenRequestRepositoryTests(LogReopenRequestRepository requests, EntityManager em) {
		this.requests = requests;
		this.fixtures = new JpaFixtures(em);
	}

	@Test
	void findByStatusOrderByRequestedAtAsc_returnsPendingQueueWithLogSiteAndRequester() {
		fixtures.flushAndClear();
		Statistics statistics = fixtures.freshStatistics();

		List<LogReopenRequest> pending = requests.findByStatusOrderByRequestedAtAsc(ReopenStatus.PENDING);

		assertThat(pending).singleElement().satisfies(request -> {
			assertThat(request.getDailyLog().getSite().getCode()).isEqualTo("ST-01");
			assertThat(request.getRequestedBy().getEmail()).isEqualTo(JpaFixtures.MANAGER_SPLIT);
		});
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	@Test
	void findByDailyLogIdAndStatus_findsOnlyTheRequestInThatStatus() {
		long st01 = fixtures.dailyLog("ST-01", "2026-10-27").getId();

		assertThat(requests.findByDailyLog_IdAndStatus(st01, ReopenStatus.PENDING)).isPresent();
		assertThat(requests.findByDailyLog_IdAndStatus(st01, ReopenStatus.APPROVED)).isEmpty();
		assertThat(requests.existsByDailyLog_IdAndStatus(st01, ReopenStatus.PENDING)).isTrue();
	}

	@Test
	void findByDailyLogIdOrderByRequestedAtDesc_returnsHistoryWithDecider() {
		long zg01 = fixtures.dailyLog("ZG-01", "2026-10-28").getId();

		assertThat(requests.findByDailyLog_IdOrderByRequestedAtDesc(zg01)).singleElement().satisfies(request -> {
			assertThat(request.getStatus()).isEqualTo(ReopenStatus.APPROVED);
			assertThat(request.getApprovedBy().getEmail()).isEqualTo(JpaFixtures.SUPERVISOR);
		});
	}

	@Test
	void existsByDailyLogId_isFalseForALogThatWasNeverReopened() {
		assertThat(requests.existsByDailyLog_Id(fixtures.dailyLog("ZG-01", "2026-10-28").getId())).isTrue();
		assertThat(requests.existsByDailyLog_Id(fixtures.dailyLog("ZG-01", "2026-10-29").getId())).isFalse();
	}

}
