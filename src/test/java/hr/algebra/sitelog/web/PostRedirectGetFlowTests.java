package hr.algebra.sitelog.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.TestcontainersConfiguration;

/**
 * Smoke test of the screens' POST-Redirect-GET contract: every POST answers with a redirect and
 * a flash KEY (the layout resolves the text). Only flows that redirect are checked here; pages
 * that render a template are covered once the templates exist (M2-09 / M2-10).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Transactional
class PostRedirectGetFlowTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	private long id(String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}

	@Test
	void createCrewRedirectsToTheListWithASuccessFlash() throws Exception {
		mvc.perform(post("/crews").with(csrf())
				.param("name", "PRG test crew").param("company", "Test d.o.o.").param("trade", "Painting")
				.param("active", "true"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/crews"))
			.andExpect(flash().attribute("flashSuccess", "crew.flash.created"));
	}

	@Test
	void deletingACrewInUseIsRefusedWithAnErrorFlash() throws Exception {
		long crewId = id("select id from crew where name = 'Kovač Formwork'");
		mvc.perform(post("/crews/{id}/delete", crewId).with(csrf()))
			.andExpect(redirectedUrl("/crews"))
			.andExpect(flash().attribute("flashError", "crew.flash.deleteBlocked"));
	}

	@Test
	void deletingASiteWithLogsIsRefusedWithAnErrorFlash() throws Exception {
		long siteId = id("select id from site where code = 'ZG-01'");
		mvc.perform(post("/sites/{id}/delete", siteId).with(csrf()))
			.andExpect(redirectedUrl("/sites/" + siteId))
			.andExpect(flash().attribute("flashError", "site.flash.deleteBlocked"));
	}

	@Test
	void changingAChildOfASignedOffLogRedirectsToTheLogWithTheLockedFlash() throws Exception {
		long logId = id("""
				select dl.id from daily_log dl join site s on s.id = dl.site_id
				where s.code = 'ZG-01' and dl.log_date = date '2026-10-26' and dl.status = 'SIGNED_OFF'""");
		long entryId = id("select min(id) from work_entry where daily_log_id = " + logId);
		long version = id("select version from daily_log where id = " + logId);
		mvc.perform(post("/daily-logs/{logId}/work-entries/{id}/delete", logId, entryId).with(csrf())
				.param("logVersion", String.valueOf(version)))
			.andExpect(redirectedUrl("/daily-logs/" + logId))
			.andExpect(flash().attribute("flashError", "dailyLog.flash.locked"));
	}

	@Test
	void markAllNotificationsReadRedirectsBack() throws Exception {
		mvc.perform(post("/notifications/read-all").with(csrf()).header("Referer", "http://localhost/sites?q=zg"))
			.andExpect(redirectedUrl("/sites?q=zg"));
	}

	@Test
	void anExternalRefererIsNotFollowed() throws Exception {
		mvc.perform(post("/notifications/read-all").with(csrf()).header("Referer", "https://evil.example/phish"))
			.andExpect(redirectedUrl("/notifications"));
	}

}
