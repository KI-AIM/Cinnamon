package de.kiaim.cinnamon.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Constraint that validates if all column names of the given ColumnConfigurations are unique.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueColumnNamesValidator.class)
@Documented
public @interface UniqueColumnNamesConstraint {
	String message() default "All column names must be unique!";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
