package de.kiaim.cinnamon.model.validation;

import jakarta.validation.Payload;

/**
 * Constraint that validates if all column indices of the given ColumnConfigurations are unique.
 * Implemented by the {@link UniqueColumnIndexValidator} class.
 *
 * @author Daniel Preciado-Marquez
 */
public @interface UniqueColumnIndexConstraint {

	String message() default "All column indices must be unique!";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
