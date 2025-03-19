package de.kiaim.cinnamon.platform.model.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class FloatRangeValidator implements ConstraintValidator<FloatRange, Float> {

	private Float min;
	private Float max;

	@Override public void initialize(FloatRange constraintAnnotation) {
		min = constraintAnnotation.min();
		max = constraintAnnotation.max();
	}

	@Override public boolean isValid(Float value, ConstraintValidatorContext context) {
		return value >= min && value <= max;
	}
}
