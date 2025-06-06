package de.kiaim.cinnamon.model.validation;

import de.kiaim.cinnamon.model.enumeration.DataType;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that a data type is not {@link DataType#UNDEFINED}.
 *
 * @author Daniel Preciado-Marquez
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DataTypeNotUndefinedValidator.class)
@Documented
public @interface DataTypeNotUndefined {
	String message() default "The data type must not be 'UNDEFINED'!";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
