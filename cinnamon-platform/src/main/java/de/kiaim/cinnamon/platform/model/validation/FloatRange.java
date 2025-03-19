package de.kiaim.cinnamon.platform.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FloatRangeValidator.class)
@Documented
public @interface FloatRange {
	String message() default "Value must be between {min} and {max}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	float min() default Float.MIN_VALUE;

	float max() default Float.MAX_VALUE;
}
