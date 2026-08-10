package de.kiaim.cinnamon.platform.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that the credentials required for SMTP authentication are present if the authentication is enabled.
 *
 * @author Daniel Preciado-Marquez
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SmtpAuthCredentialsValidator.class)
@Documented
public @interface SmtpAuthCredentials {

	String message() default "Username must not be blank if SMTP authentication is enabled.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
