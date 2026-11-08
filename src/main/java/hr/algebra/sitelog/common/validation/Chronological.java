package hr.algebra.sitelog.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Class-level check that property {@link #before()} is not after property {@link #after()}
 * (both {@link Comparable}, e.g. dates or times). The violation is reported on the
 * {@link #after()} field, so the form shows it next to the end date / end time.
 * Nulls are not checked (use {@code @NotNull} for that).
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ChronologicalValidator.class)
@Repeatable(Chronological.List.class)
public @interface Chronological {

	String before();

	String after();

	/** {@code true}: equal values are invalid too (end time must be strictly after start time). */
	boolean strict() default false;

	String message();

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	@Documented
	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	@interface List {

		Chronological[] value();

	}

}
