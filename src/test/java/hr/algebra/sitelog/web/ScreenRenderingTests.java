package hr.algebra.sitelog.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.List;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.Cookie;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import hr.algebra.sitelog.TestcontainersConfiguration;

/**
 * Renders every screen of the daily log aggregate with the seed data, in English and Croatian:
 * status 200, no template or expression error, the texts of the prototype on the page. Also the
 * states the prototype draws: empty list, validation errors, locked log, blocked delete.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
@Transactional
class ScreenRenderingTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	private long id(String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}

	private long siteId(String code) {
		return id("select id from site where code = '" + code + "'");
	}

	private long logId(String code, String date) {
		return id("select dl.id from daily_log dl join site s on s.id = dl.site_id where s.code = '" + code
				+ "' and dl.log_date = date '" + date + "'");
	}

	private List<String> pages() {
		long zg01 = siteId("ZG-01");
		long signed = logId("ZG-01", "2026-10-26");
		long reopened = logId("ZG-01", "2026-10-28");
		long draft = logId("ZG-01", "2026-10-29");
		long entry = id("select min(id) from work_entry where daily_log_id = " + draft);
		long assignment = id("select min(id) from crew_assignment where work_entry_id = " + entry);
		long crew = id("select id from crew where name = 'Kovač Formwork'");
		return List.of("/sites", "/sites?status=ACTIVE", "/sites?status=FINISHED", "/sites?q=nothing-matches",
				"/sites/" + zg01, "/sites/" + zg01 + "?tab=logs", "/sites/" + zg01 + "?tab=incidents",
				"/sites/" + zg01 + "?tab=compliance", "/sites/new", "/sites/" + zg01 + "/edit",
				"/sites/" + zg01 + "/daily-logs/new", "/daily-logs/" + signed, "/daily-logs/" + reopened,
				"/daily-logs/" + draft, "/daily-logs/" + draft + "/edit", "/daily-logs/" + draft + "/work-entries/new",
				"/daily-logs/" + draft + "/work-entries/" + entry + "/edit",
				"/daily-logs/" + draft + "/work-entries/" + entry + "/crew-assignments/new",
				"/daily-logs/" + draft + "/crew-assignments/" + assignment + "/edit", "/crews", "/crews/new",
				"/crews/" + crew + "/edit", "/notifications");
	}

	@ParameterizedTest
	@ValueSource(strings = { "en", "hr" })
	void everyScreenRendersInBothLanguages(String lang) throws Exception {
		for (String page : pages()) {
			MvcResult result = mvc.perform(get(page).cookie(new Cookie("lang", lang)))
				.andExpect(status().isOk())
				.andReturn();
			String html = result.getResponse().getContentAsString();
			// a missing key renders as ??key_locale??
			if (html.contains("??")) {
				throw new AssertionError("Missing message key on " + page + " (" + lang + "): "
						+ html.substring(html.indexOf("??"), Math.min(html.length(), html.indexOf("??") + 80)));
			}
		}
	}

	@Test
	void croatianIsUsedWhenChosen() throws Exception {
		mvc.perform(get("/sites").cookie(new Cookie("lang", "hr")))
			.andExpect(content().string(containsString("Novo gradilište")))
			.andExpect(content().string(containsString("Residential Complex Lanište")));
	}

	@Test
	void finishedFilterShowsTheEmptyFilterState() throws Exception {
		mvc.perform(get("/sites").param("status", "FINISHED"))
			.andExpect(content().string(containsString("No sites match the filter")))
			.andExpect(content().string(not(containsString("Residential Complex Lanište"))));
	}

	@Test
	void signedOffLogIsReadOnly() throws Exception {
		long signed = logId("ZG-01", "2026-10-26");
		mvc.perform(get("/daily-logs/{id}", signed))
			.andExpect(content().string(containsString("Signed off and locked")))
			.andExpect(content().string(not(containsString("/daily-logs/" + signed + "/edit"))))
			.andExpect(content().string(not(containsString("Remove entry"))))
			.andExpect(content().string(not(containsString("sign-off-form"))));
	}

	@Test
	void draftLogOffersEditSignOffAndDelete() throws Exception {
		long draft = logId("ZG-02", "2026-10-28");
		mvc.perform(get("/daily-logs/{id}", draft))
			.andExpect(content().string(containsString("/daily-logs/" + draft + "/edit")))
			.andExpect(content().string(containsString("/daily-logs/" + draft + "/sign-off")))
			.andExpect(content().string(containsString("/daily-logs/" + draft + "/delete")));
	}

	@Test
	void draftLinkedToAnIncidentHasNoDeleteButton() throws Exception {
		long linked = logId("ZG-01", "2026-10-29");
		mvc.perform(get("/daily-logs/{id}", linked))
			.andExpect(content().string(containsString("/daily-logs/" + linked + "/sign-off")))
			.andExpect(content().string(not(containsString("/daily-logs/" + linked + "/delete"))));
	}

	@Test
	void siteWithRecordsExplainsWhyItCannotBeDeleted() throws Exception {
		mvc.perform(get("/sites/{id}", siteId("ZG-01")))
			.andExpect(content().string(containsString("This site cannot be deleted")))
			.andExpect(content().string(not(containsString("/delete\""))));
	}

	@Test
	void newSiteHasTheEmptyDailyLogState() throws Exception {
		long manager = id("select id from app_user where email = 'manager@sitelog.test'");
		MvcResult created = mvc.perform(post("/sites").with(csrf())
				.param("code", "ZG-99").param("name", "Empty test site").param("address", "Ilica 1, Zagreb")
				.param("startDate", "2026-11-01").param("managerId", String.valueOf(manager)))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		String detail = created.getResponse().getRedirectedUrl();
		mvc.perform(get(detail).param("tab", "logs"))
			.andExpect(content().string(containsString("No daily logs yet")))
			.andExpect(content().string(containsString("Delete this site?")));
	}

	@Test
	void emptyDailyLogShowsThePrototypeMessages() throws Exception {
		mvc.perform(post("/sites/{id}/daily-logs", siteId("ZG-01")).with(csrf())
				.param("action", "save").param("logDate", ""))
			.andExpect(status().isOk())
			.andExpect(view().name("dailylog/form"))
			.andExpect(content().string(containsString("Daily log could not be saved")))
			.andExpect(content().string(containsString("Date is required")))
			.andExpect(content().string(containsString("Add at least one work entry")));
	}

	@Test
	void hoursOutOfRangeShowTheMessageOnTheCrewRow() throws Exception {
		long crew = id("select id from crew where name = 'Babić Concrete'");
		mvc.perform(post("/sites/{id}/daily-logs", siteId("ZG-02")).with(csrf())
				.param("action", "save").param("logDate", LocalDate.now().toString())
				.param("workEntries[0].description", "Slab pour")
				.param("workEntries[0].crewAssignments[0].crewId", String.valueOf(crew))
				.param("workEntries[0].crewAssignments[0].hoursWorked", "30")
				.param("workEntries[0].crewAssignments[0].workerCount", "4"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Hours worked must be between 0 and 24")))
			.andExpect(content().string(containsString("workEntries0.crewAssignments0.hoursWorked-error")));
	}

	@Test
	void rowButtonsAddAWorkEntryWithoutSaving() throws Exception {
		mvc.perform(post("/sites/{id}/daily-logs", siteId("ZG-02")).with(csrf())
				.param("action", "addEntry").param("logDate", "2026-11-02")
				.param("workEntries[0].description", "First"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("workEntries[1].description")))
			.andExpect(content().string(containsString("value=\"removeEntry-1\"")))
			.andExpect(content().string(not(containsString("Date is required"))));
	}

	@Test
	void createLogWithTwoEntriesAndCrewsThenShowIt() throws Exception {
		long siteId = siteId("ZG-02");
		long babic = id("select id from crew where name = 'Babić Concrete'");
		long kovac = id("select id from crew where name = 'Kovač Formwork'");
		long novak = id("select id from crew where name = 'Elektro Novak'");
		mvc.perform(post("/sites/{id}/daily-logs", siteId).with(csrf())
				.param("action", "save").param("logDate", "2026-11-03").param("weather", "CLOUDY")
				.param("notes", "Two activities")
				.param("workEntries[0].description", "Slab pour level 4")
				.param("workEntries[0].startTime", "07:00").param("workEntries[0].endTime", "15:00")
				.param("workEntries[0].crewAssignments[0].crewId", String.valueOf(babic))
				.param("workEntries[0].crewAssignments[0].hoursWorked", "7.5")
				.param("workEntries[0].crewAssignments[0].workerCount", "6")
				.param("workEntries[0].crewAssignments[1].crewId", String.valueOf(kovac))
				.param("workEntries[0].crewAssignments[1].hoursWorked", "4")
				.param("workEntries[0].crewAssignments[1].workerCount", "2")
				.param("workEntries[1].description", "Cable trays")
				.param("workEntries[1].crewAssignments[0].crewId", String.valueOf(novak))
				.param("workEntries[1].crewAssignments[0].hoursWorked", "8")
				.param("workEntries[1].crewAssignments[0].workerCount", "3"))
			.andExpect(redirectedUrl("/sites/" + siteId));
		long created = logId("ZG-02", "2026-11-03");
		mvc.perform(get("/daily-logs/{id}", created))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("2 work entries")))
			.andExpect(content().string(containsString("Slab pour level 4")))
			.andExpect(content().string(containsString("Elektro Novak")))
			.andExpect(content().string(containsString("7.5 hrs")));
	}

	@Test
	void duplicateCrewNameIsShownOnTheField() throws Exception {
		mvc.perform(post("/crews").with(csrf())
				.param("name", "Kovač Formwork").param("company", "X d.o.o.").param("trade", "Formwork"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Crew could not be created")))
			.andExpect(content().string(containsString("Name is already used")));
	}

	@Test
	void unknownRecordGivesTheOwn404Page() throws Exception {
		mvc.perform(get("/sites/{id}", 999_999))
			.andExpect(status().isNotFound())
			.andExpect(view().name("error/404"))
			.andExpect(content().string(containsString("Page not found")))
			.andExpect(content().string(containsString("does not exist")));
	}

	/** The error dispatch of the servlet container, as Spring Boot's BasicErrorController receives it. */
	@ParameterizedTest
	@ValueSource(ints = { 403, 404, 500, 400 })
	void containerErrorsUseTheOwnPagesWithoutDetails(int code) throws Exception {
		String expected = switch (code) {
			case 403 -> "Access denied";
			case 404 -> "Page not found";
			case 500 -> "Something went wrong";
			default -> "The request could not be processed";
		};
		mvc.perform(get("/error")
				.requestAttr(RequestDispatcher.ERROR_STATUS_CODE, code)
				.requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/sites/broken")
				.requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("secret internal detail"))
				.accept(MediaType.TEXT_HTML))
			.andExpect(status().is(code))
			.andExpect(content().string(containsString(expected)))
			.andExpect(content().string(not(containsString("secret internal detail"))))
			.andExpect(content().string(not(containsString("IllegalStateException"))));
	}

	@Test
	void staleVersionGivesTheOwn409Page() throws Exception {
		long draft = logId("ZG-01", "2026-10-29");
		mvc.perform(post("/daily-logs/{id}/sign-off", draft).with(csrf()).param("version", "-1"))
			.andExpect(status().isConflict())
			.andExpect(view().name("error/409"))
			.andExpect(content().string(containsString("changed by someone else")))
			.andExpect(content().string(containsString("Reload the page")));
	}

}
