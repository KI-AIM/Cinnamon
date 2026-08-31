package de.kiaim.cinnamon.platform.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Annotation to validate email content.
 * Ensures that either a template is specified or both a custom subject and body are provided.
 * Implemented by {@link EmailContentValidator}.
 *
 * @author Daniel Preciado-Marquez
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EmailContentValidator.class)
@Documented
public @interface EmailContent {

	String message() default "Invalid email content";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
