package de.kiaim.platform.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MatchingNumberColumnsValidator.class)
@Documented
public @interface MatchingNumberColumnsConstraint {
	String message() default "Number of columns does not match!";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
