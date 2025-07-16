package de.kiaim.cinnamon.platform.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates if a string mets the configured password requirements.
 *
 * @author Daniel Preciado-Marquez
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordRequirementsValidator.class)
@Documented
public @interface PasswordRequirements {
	String message() default "Password requirements not met!";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
