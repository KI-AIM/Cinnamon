package de.kiaim.cinnamon.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that the data source configuration is valid for the selected data source type.
 * <br>
 * Implemented by {@link DataSourceConfiguredValidator}.
 *
 * @author Daniel Preciado-Marquez
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DataSourceConfiguredValidator.class)
@Documented
public @interface DataSourceConfigured {
	String message() default "Missing configuration for the selected data source type!";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
