package de.kiaim.cinnamon.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Constraint that validates if all column indices of the given ColumnConfigurations are unique.
 * Implemented by the {@link UniqueColumnIndexValidator} class.
 *
 * @author Daniel Preciado-Marquez
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueColumnIndexValidator.class)
@Documented
public @interface UniqueColumnIndexConstraint {

	String message() default "All column indices must be unique!";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
