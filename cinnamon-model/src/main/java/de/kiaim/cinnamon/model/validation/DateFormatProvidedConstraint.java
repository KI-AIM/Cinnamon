package de.kiaim.cinnamon.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that an attribute with type {@link de.kiaim.cinnamon.model.enumeration.DataType#DATE} or
 * {@link de.kiaim.cinnamon.model.enumeration.DataType#DATE_TIME} has a
 * {@link de.kiaim.cinnamon.model.configuration.data.DateFormatConfiguration} or a
 * {@link de.kiaim.cinnamon.model.configuration.data.DateTimeFormatConfiguration} respectively.
 * Implemented by {@link DateFormatProvidedValidator}.
 *
 * @author Daniel Preciado-Marquez
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DateFormatProvidedValidator.class)
@Documented
public @interface DateFormatProvidedConstraint {
	String message() default "No format provided!";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
