package de.kiaim.cinnamon.platform.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that an email template contains at most one content per language.
 *
 * @author Daniel Preciado-Marquez
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueTemplateLanguagesValidator.class)
@Documented
public @interface UniqueTemplateLanguages {

	String message() default "Each language must not be configured more than once.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
