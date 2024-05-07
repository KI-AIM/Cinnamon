package de.kiaim.platform.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DataConfigurationValidator.class)
@Documented
public @interface DataConfigurationConstraint {
	String message() default "The data configuration is not valid!";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
