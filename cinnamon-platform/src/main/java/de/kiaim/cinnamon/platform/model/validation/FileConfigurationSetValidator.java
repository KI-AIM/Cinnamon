package de.kiaim.cinnamon.platform.model.validation;

import de.kiaim.cinnamon.platform.model.file.FileConfiguration;
import jakarta.validation.*;
import org.springframework.lang.Nullable;

import java.util.Set;

/**
 * Validates if the corresponding file configuration for the file type defined {@link FileConfiguration#getFileType()} is set.
 * Additionally, validates only the configuration that matches the selected file type.
 *
 * @author Daniel Preciado-Marquez
 */
public class FileConfigurationSetValidator implements ConstraintValidator<FileConfigurationSet, FileConfiguration> {

	@Override
	public boolean isValid(@Nullable final FileConfiguration value, final ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}

		// Validate only the matching configuration object
		return switch (value.getFileType()) {
			case CSV -> validateFileConfiguration(value.getCsvFileConfiguration(), "csvFileConfiguration", context,
			                                      "CSV file configuration must be set for CSV files!");
			case FHIR -> validateFileConfiguration(value.getFhirFileConfiguration(), "fhirFileConfiguration", context,
			                                       "FHIR file configuration must be set for FHIR bundles!");
			case XLSX -> validateFileConfiguration(value.getXlsxFileConfiguration(), "xlsxFileConfiguration", context,
			                                       "XLSX file configuration must be set for XLSX files!");
		};
	}

	/**
	 * Validates the given concrete file configuration of a {@link FileConfiguration}.
	 *
	 * @param fileConfiguration The concrete file configuration.
	 * @param propertyPath      The field name of the configuration inside the {@link FileConfiguration}.
	 * @param context           The validation context.
	 * @param missingMessage    The error message in case the given configuration is null.
	 * @param <T>               A concrete file configuration
	 *                          {@link de.kiaim.cinnamon.platform.model.file.CsvFileConfiguration},
	 *                          {@link de.kiaim.cinnamon.platform.model.file.FhirFileConfiguration} or
	 *                          {@link de.kiaim.cinnamon.platform.model.file.XlsxFileConfiguration}
	 * @return If the configuration is valid.
	 */
	private <T> boolean validateFileConfiguration(@Nullable final T fileConfiguration, final String propertyPath,
	                                              final ConstraintValidatorContext context,
	                                              final String missingMessage) {
		// Ensure the proper configuration object is provided for the selected file type
		if (fileConfiguration == null) {
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(missingMessage)
			       .addPropertyNode(propertyPath).addConstraintViolation();
			return false;
		} else {

			// Validate file configuration object
			try (final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
				final Validator validator = validatorFactory.getValidator();

				Set<ConstraintViolation<T>> violations = validator.validate(fileConfiguration);
				if (!violations.isEmpty()) {
					context.disableDefaultConstraintViolation();
					for (ConstraintViolation<T> v : violations) {
						context.buildConstraintViolationWithTemplate(v.getMessage())
						       .addPropertyNode(propertyPath)
						       .addPropertyNode(v.getPropertyPath().toString())
						       .addConstraintViolation();
					}
					return false;
				}
			}
		}

		return true;
	}
}
