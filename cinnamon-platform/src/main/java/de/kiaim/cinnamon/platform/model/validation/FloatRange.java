package de.kiaim.cinnamon.platform.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates min and max values of floats.
 * {@code null} values are considered valid.
 * Jakartas {@link jakarta.validation.constraints.Min} and {@link jakarta.validation.constraints.Max} functions don't support float values and causing issues with swagger.
 * Implemented by {@link FloatRangeValidator}.
 *
 * @author Daniel Preciado-Marquez
 */
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
