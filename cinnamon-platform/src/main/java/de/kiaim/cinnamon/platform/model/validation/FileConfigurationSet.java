package de.kiaim.cinnamon.platform.model.validation;

import de.kiaim.cinnamon.platform.model.file.FileConfiguration;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates if the corresponding file configuration for the file type defined {@link FileConfiguration#getFileType()} is set.
 *
 * @author Daniel Preciado-Marquez
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FileConfigurationSetValidator.class)
@Documented
public @interface FileConfigurationSet {
	String message() default "The file configuration for the specified file type must be set!";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
