package de.kiaim.cinnamon.platform.model.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates min and max values for floats.
 * {@code null} values are considered valid.
 * Implementation for {@link FloatRange}.
 *
 * @author Daniel Preciado-Marquez
 */
public class FloatRangeValidator implements ConstraintValidator<FloatRange, Float> {

	private Float min;
	private Float max;

	@Override public void initialize(FloatRange constraintAnnotation) {
		min = constraintAnnotation.min();
		max = constraintAnnotation.max();
	}

	@Override public boolean isValid(Float value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}
		return value >= min && value <= max;
	}
}
