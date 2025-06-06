package de.kiaim.cinnamon.platform.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EmailAvailableValidator.class)
@Documented
public @interface EmailAvailable {

	String message() default "Email is not available!";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
