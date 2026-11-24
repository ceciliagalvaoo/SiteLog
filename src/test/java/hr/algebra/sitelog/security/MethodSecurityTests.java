package hr.algebra.sitelog.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.TestcontainersConfiguration;
import hr.algebra.sitelog.crew.service.CrewCommand;
import hr.algebra.sitelog.crew.service.CrewService;
import hr.algebra.sitelog.dailylog.domain.Weather;
import hr.algebra.sitelog.dailylog.service.DailyLogCommand;
import hr.algebra.sitelog.dailylog.service.DailyLogService;
import hr.algebra.sitelog.site.service.SiteService;

/**
 * {@code @PreAuthorize} on the services is active in the real context: role rules and the "own
 * site" rule of the site manager. The full matrix of 403s per role is in the security test task.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class MethodSecurityTests {

	@Autowired
	CrewService crewService;

	@Autowired
	SiteService siteService;

	@Autowired
	DailyLogService dailyLogService;

	@Autowired
	JdbcTemplate jdbc;

	private long siteId(String code) {
		return jdbc.queryForObject("select id from site where code = ?", Long.class, code);
	}

	private static DailyLogCommand emptyLog() {
		return new DailyLogCommand(LocalDate.of(2026, 11, 2), Weather.SUNNY, null, List.of());
	}

	@Test
	void anonymousCallerIsRefused() {
		assertThatThrownBy(() -> siteService.list("", null, Pageable.unpaged()))
			.isInstanceOf(AuthenticationCredentialsNotFoundException.class);
	}

	@Test
	@WithUserDetails("inspector@sitelog.test")
	void inspectorCannotCreateACrew() {
		assertThatThrownBy(() -> crewService.create(new CrewCommand("Inspector crew", "Co", "Trade", true)))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@WithUserDetails("admin@sitelog.test")
	void adminCreatesACrew() {
		assertThatCode(() -> crewService.create(new CrewCommand("Admin crew", "Co", "Trade", true)))
			.doesNotThrowAnyException();
	}

	@Test
	@WithUserDetails("admin@sitelog.test")
	void adminDoesNotWriteDailyLogs() {
		assertThatThrownBy(() -> dailyLogService.create(siteId("ZG-01"), emptyLog()))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@WithUserDetails("manager.split@sitelog.test")
	void siteManagerCannotWriteLogsOnAnotherManagersSite() {
		assertThatThrownBy(() -> dailyLogService.create(siteId("ZG-01"), emptyLog()))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@WithUserDetails("manager@sitelog.test")
	void siteManagerWritesLogsOnOwnSite() {
		assertThatCode(() -> dailyLogService.create(siteId("ZG-01"), emptyLog())).doesNotThrowAnyException();
	}

	@Test
	@WithUserDetails("manager.split@sitelog.test")
	void siteManagerCannotSignOffALogOfAnotherSite() {
		long log = jdbc.queryForObject("""
				select dl.id from daily_log dl join site s on s.id = dl.site_id
				where s.code = 'ZG-01' and dl.log_date = date '2026-10-29'""", Long.class);
		assertThatThrownBy(() -> dailyLogService.signOff(log, 0L)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@WithUserDetails("supervisor@sitelog.test")
	void everySignedInUserReadsSites() {
		assertThatCode(() -> siteService.get(siteId("ZG-01"))).doesNotThrowAnyException();
	}

}
