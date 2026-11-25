package hr.algebra.sitelog.api.error;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One invalid field of a request, in the {@code errors} member of a 400 / 422 ProblemDetail.
 *
 * @param field   property path in the request body, e.g. {@code workEntries[0].crewAssignments[1].hoursWorked}
 * @param code    stable message key (clients can switch on it), e.g. {@code validation.site.code.duplicate}
 * @param message the text in the request's language (en / hr)
 */
@Schema(description = "One invalid field of the request")
public record FieldViolation(
		@Schema(example = "code") String field,
		@Schema(example = "validation.site.code.duplicate") String code,
		@Schema(example = "Code is already used") String message) {
}
