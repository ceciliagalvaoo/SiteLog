package hr.algebra.sitelog.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import hr.algebra.sitelog.dailylog.web.WorkEntryForm;
import hr.algebra.sitelog.site.web.SiteForm;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * {@code @Chronological} on the real forms, with plain Bean Validation (no Spring): the end time
 * of a work entry must be strictly after the start ({@code ck_work_entry_times}); a site may end
 * on the day it starts.
 */
class ChronologicalValidatorTests {

	static ValidatorFactory factory;

	static Validator validator;

	@BeforeAll
	static void setUp() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void tearDown() {
		factory.close();
	}

	private static WorkEntryForm entry(LocalTime start, LocalTime end) {
		WorkEntryForm form = WorkEntryForm.blank();
		form.setCrewAssignments(List.of());
		form.setDescription("Formwork");
		form.setStartTime(start);
		form.setEndTime(end);
		return form;
	}

	private static List<String> pathsOf(Object form, String property) {
		return validator.validate(form).stream()
			.map(ConstraintViolation::getPropertyPath)
			.map(Object::toString)
			.filter(property::equals)
			.toList();
	}

	@Test
	void workEntry_whenEndTimeBeforeStartTime_reportsViolationOnEndTime() {
		WorkEntryForm form = entry(LocalTime.of(15, 0), LocalTime.of(7, 0));

		assertThat(validator.validate(form)).singleElement().satisfies(v -> {
			assertThat(v.getPropertyPath()).hasToString("endTime");
			assertThat(v.getMessageTemplate()).isEqualTo("{validation.workEntry.endTime.afterStart}");
		});
	}

	@Test
	void workEntry_whenEndTimeEqualsStartTime_isInvalidBecauseTheCheckIsStrict() {
		assertThat(pathsOf(entry(LocalTime.of(7, 0), LocalTime.of(7, 0)), "endTime")).hasSize(1);
	}

	@Test
	void workEntry_whenEndTimeAfterStartTime_isValid() {
		assertThat(validator.validate(entry(LocalTime.of(7, 0), LocalTime.of(15, 0)))).isEmpty();
	}

	@Test
	void workEntry_whenATimeIsMissing_isNotCheckedByChronological() {
		assertThat(validator.validate(entry(null, LocalTime.of(7, 0)))).isEmpty();
		assertThat(validator.validate(entry(LocalTime.of(7, 0), null))).isEmpty();
	}

	@Test
	void site_whenEndDateBeforeStartDate_reportsViolationOnEndDate() {
		SiteForm form = new SiteForm();
		form.setStartDate(LocalDate.of(2026, 5, 1));
		form.setEndDate(LocalDate.of(2026, 4, 30));

		assertThat(pathsOf(form, "endDate")).hasSize(1);
	}

	@Test
	void site_whenEndDateEqualsStartDate_isValidBecauseTheCheckIsNotStrict() {
		SiteForm form = new SiteForm();
		form.setStartDate(LocalDate.of(2026, 5, 1));
		form.setEndDate(LocalDate.of(2026, 5, 1));

		assertThat(pathsOf(form, "endDate")).isEmpty();
	}

}
