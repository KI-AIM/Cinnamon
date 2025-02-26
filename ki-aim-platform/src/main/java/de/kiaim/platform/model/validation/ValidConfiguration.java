package de.kiaim.platform.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that if the step should not be skipped, all necessary data is present.
 *
 * @author Daniel Preciado-Marquez
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidConfigurationValidator.class)
@Documented
public @interface ValidConfiguration {
	String message() default "Configuration not set!";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
