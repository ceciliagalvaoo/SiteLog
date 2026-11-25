package hr.algebra.sitelog.api.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import hr.algebra.sitelog.common.exception.DeletionNotAllowedException;
import hr.algebra.sitelog.common.exception.DomainException;
import hr.algebra.sitelog.common.exception.InvalidFieldException;
import hr.algebra.sitelog.common.exception.InvalidTransitionException;
import hr.algebra.sitelog.common.exception.LogLockedException;
import hr.algebra.sitelog.common.exception.MissingSeverityFieldsException;
import hr.algebra.sitelog.common.exception.NotFoundException;
import hr.algebra.sitelog.dailylog.domain.DailyLog;
import hr.algebra.sitelog.incident.domain.Incident;
import hr.algebra.sitelog.site.domain.Site;
import hr.algebra.sitelog.support.WebSliceConfig;
import hr.algebra.sitelog.support.WebSliceTest;
import jakarta.servlet.http.Cookie;
import jakarta.validation.constraints.NotBlank;

/**
 * Every status of the API error contract against a stub controller in the API package: each
 * one answers {@code application/problem+json} with status, detail, instance and the stable
 * {@code code}, never the HTML error page of the screens.
 */
@WebMvcTest(controllers = ApiExceptionHandlerTests.ProblemController.class)
@Import({ WebSliceConfig.class, ApiExceptionHandlerTests.ProblemController.class })
@WithMockUser
class ApiExceptionHandlerTests extends WebSliceTest {

	record Body(@NotBlank(message = "{validation.site.name.required}") String name) {
	}

	/** Throws whatever the test asks for; nested, so only this test registers it. */
	@RestController
	static class ProblemController {

		@GetMapping("/api/test/{what}")
		String fail(@PathVariable String what) {
			throw switch (what) {
				case "not-found" -> new NotFoundException(Site.class, 9L);
				case "locked" -> new LogLockedException(4L);
				case "transition" -> new InvalidTransitionException("dailyLog.flash.alreadySignedOff", "signed");
				case "delete" -> new DeletionNotAllowedException("site.flash.deleteBlocked", "records");
				case "field" -> new InvalidFieldException("code", "validation.site.code.duplicate");
				case "rule" -> new DomainException("incident.flash.missingFields", "missing") {
				};
				case "stale" -> new ObjectOptimisticLockingFailureException(DailyLog.class, 4L);
				case "stale-incident" -> new ObjectOptimisticLockingFailureException(Incident.class, 12L);
				case "missing" -> new MissingSeverityFieldsException(12L, List.of("witnesses", "medicalTreatment"));
				case "constraint" -> new DataIntegrityViolationException("uk_site_code");
				case "trigger" -> new UncategorizedSQLException("update", "update daily_log",
						new SQLException("ERROR: daily_log_locked", "P0001"));
				case "denied" -> new AuthorizationDeniedException("Access Denied", new AuthorizationDecision(false));
				default -> new IllegalStateException("secret internals");
			};
		}

		@PostMapping("/api/test")
		String create(@Validated @RequestBody Body body) {
			return body.name();
		}

		@GetMapping("/api/test")
		String number(@RequestParam Long version) {
			return String.valueOf(version);
		}

	}

	@Test
	void notFound_is404Problem() throws Exception {
		mvc.perform(get("/api/test/not-found"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.title").value("Not Found"))
			.andExpect(jsonPath("$.code").value("error.notFound"))
			.andExpect(jsonPath("$.detail").value("The page or record you are looking for does not exist."))
			.andExpect(jsonPath("$.instance").value("/api/test/not-found"));
	}

	@Test
	void lockedLog_is409() throws Exception {
		mvc.perform(get("/api/test/locked"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("dailyLog.flash.locked"));
	}

	@Test
	void invalidTransitionAndRefusedDelete_are409() throws Exception {
		mvc.perform(get("/api/test/transition"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("dailyLog.flash.alreadySignedOff"));
		mvc.perform(get("/api/test/delete"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("site.flash.deleteBlocked"));
	}

	@Test
	void staleVersion_is409WithTheReloadMessage() throws Exception {
		mvc.perform(get("/api/test/stale"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("dailyLog.flash.conflict"))
			.andExpect(jsonPath("$.detail").value(containsString("changed by someone else")));
	}

	@Test
	void staleIncidentVersion_is409WithTheIncidentMessage() throws Exception {
		mvc.perform(get("/api/test/stale-incident"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("incident.flash.conflict"))
			.andExpect(jsonPath("$.detail").value("This incident was changed by someone else. Reload and try again."));
	}

	@Test
	void missingSeverityFields_is422WithOneErrorPerField() throws Exception {
		mvc.perform(get("/api/test/missing"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("incident.flash.missingFields"))
			.andExpect(jsonPath("$.errors.length()").value(2))
			.andExpect(jsonPath("$.errors[0].field").value("witnesses"))
			.andExpect(jsonPath("$.errors[0].code").value("validation.incident.witnesses.required"))
			.andExpect(jsonPath("$.errors[1].field").value("medicalTreatment"))
			.andExpect(jsonPath("$.errors[1].message").value("Enter treatment or \"None\""));
	}

	@Test
	void databaseConstraintAndLockTrigger_are409() throws Exception {
		mvc.perform(get("/api/test/constraint"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("error.conflict.data"));
		mvc.perform(get("/api/test/trigger"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("dailyLog.flash.locked"));
	}

	@Test
	void invalidField_is422WithTheFieldInErrors() throws Exception {
		mvc.perform(get("/api/test/field"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("validation.site.code.duplicate"))
			.andExpect(jsonPath("$.errors[0].field").value("code"))
			.andExpect(jsonPath("$.errors[0].message").value("Code is already used"));
	}

	@Test
	void otherBusinessRule_is422() throws Exception {
		mvc.perform(get("/api/test/rule"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("incident.flash.missingFields"));
	}

	@Test
	void deniedByMethodSecurity_is403Problem() throws Exception {
		mvc.perform(get("/api/test/denied"))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("error.403.body"));
	}

	@Test
	void unexpectedError_is500WithoutInternals() throws Exception {
		mvc.perform(get("/api/test/boom"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("error.500.body"))
			.andExpect(content().string(not(containsString("secret internals"))));
	}

	@Test
	void beanValidation_is400WithKeyAndMessagePerField() throws Exception {
		mvc.perform(post("/api/test").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation.request.invalid"))
			.andExpect(jsonPath("$.errors[0].field").value("name"))
			.andExpect(jsonPath("$.errors[0].code").value("validation.site.name.required"))
			.andExpect(jsonPath("$.errors[0].message").value("Name is required"));
	}

	@Test
	void malformedJsonAndMissingOrWrongParameter_are400Problems() throws Exception {
		mvc.perform(post("/api/test").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{nope"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		mvc.perform(get("/api/test"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400));
		mvc.perform(get("/api/test").param("version", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void textsFollowTheUiLanguage() throws Exception {
		mvc.perform(get("/api/test/field").cookie(new Cookie("lang", "hr")))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("validation.site.code.duplicate"))
			.andExpect(jsonPath("$.detail").value(not("Code is already used")));
	}

	@Test
	void postWithoutCsrfToken_is403ProblemFromTheFilterChain() throws Exception {
		mvc.perform(post("/api/test").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("error.csrf"));
	}

	@Test
	void anonymous_is401ProblemNotARedirect() throws Exception {
		mvc.perform(get("/api/test/not-found").with(anonymous()))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.instance").value("/api/test/not-found"));
	}

}
