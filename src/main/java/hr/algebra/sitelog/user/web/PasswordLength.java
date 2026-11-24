package hr.algebra.sitelog.user.web;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import hr.algebra.sitelog.user.service.PasswordPolicy;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new password: not blank and {@link PasswordPolicy#MIN_LENGTH} to {@link PasswordPolicy#MAX_BYTES}
 * characters. {@code @ReportAsSingleViolation} turns the two checks into ONE message, so an empty
 * field does not show both "required" and "too short". {@code min} / {@code max} fill the
 * {min} / {max} placeholders of {@code validation.password.size}.
 */
@NotBlank
@Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_BYTES)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target(FIELD)
@Retention(RUNTIME)
@Documented
public @interface PasswordLength {

	String message() default "{validation.password.size}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	int min() default PasswordPolicy.MIN_LENGTH;

	int max() default PasswordPolicy.MAX_BYTES;

}
