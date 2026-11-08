package hr.algebra.sitelog.common.validation;

import org.springframework.beans.BeanWrapperImpl;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ChronologicalValidator implements ConstraintValidator<Chronological, Object> {

	private Chronological constraint;

	@Override
	public void initialize(Chronological constraint) {
		this.constraint = constraint;
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public boolean isValid(Object value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}
		BeanWrapperImpl bean = new BeanWrapperImpl(value);
		Object first = bean.getPropertyValue(constraint.before());
		Object second = bean.getPropertyValue(constraint.after());
		if (!(first instanceof Comparable start) || second == null) {
			return true;
		}
		int comparison = start.compareTo(second);
		boolean valid = constraint.strict() ? comparison < 0 : comparison <= 0;
		if (!valid) {
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(constraint.message())
				.addPropertyNode(constraint.after())
				.addConstraintViolation();
		}
		return valid;
	}

}
