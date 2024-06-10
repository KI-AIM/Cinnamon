package de.kiaim.platform.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Constraint that validates if the number of columns in the data configuration matches the number of columns in the data.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MatchingNumberColumnsValidator.class)
@Documented
public @interface MatchingNumberColumnsConstraint {
	String message() default "Number of columns in the configuration does not match the number of columns in the data set!";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
