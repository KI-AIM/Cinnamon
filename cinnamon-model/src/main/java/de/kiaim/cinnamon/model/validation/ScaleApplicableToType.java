package de.kiaim.cinnamon.model.validation;

import de.kiaim.cinnamon.model.enumeration.DataType;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that the scale is applicable to the type of the attribute.
 * Null values and {@link DataType#UNDEFINED} data type are considered as valid.
 * Implemented by {@link ScaleApplicableToTypeValidator}.
 *
 * @author Daniel Preciado-Marquez
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ScaleApplicableToTypeValidator.class)
@Documented
public @interface ScaleApplicableToType {
	String message() default "The data scale is not applicable to the specified data type!";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
