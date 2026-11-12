package de.kiaim.cinnamon.model.validation;

import de.kiaim.cinnamon.model.configuration.data.DataSourceConfiguration;
import de.kiaim.cinnamon.model.configuration.data.DataSourceServerConfiguration;
import jakarta.validation.*;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Validates that the data source configuration is valid for the selected data source type.
 * <br>
 * Can be used with {@link DataSourceConfigured}.
 *
 * @author Daniel Preciado-Marquez
 */
public class DataSourceConfiguredValidator implements ConstraintValidator<DataSourceConfigured, DataSourceConfiguration> {

	@Override
	public boolean isValid(@Nullable final DataSourceConfiguration value, final ConstraintValidatorContext context) {
		if (value == null || value.getDataSourceType() == null) {
			return true; // @NotNull handles null values if needed
		}

		return switch (value.getDataSourceType()) {
			case LOCAL -> true; // No additional configuration required for local data source
			case FHIR_SERVER -> validateFhirServerConfiguration(value, context);
		};
	}

	private boolean validateFhirServerConfiguration(final DataSourceConfiguration value,
	                                                final ConstraintValidatorContext context) {
		if (value.getServer() == null) {
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(
					       "Server configuration is required for FHIR server data source!")
			       .addPropertyNode("server")
			       .addConstraintViolation();
			return false;
		} else {
			// Validate the server configuration object
			try (final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
				final Validator validator = validatorFactory.getValidator();

				Set<ConstraintViolation<DataSourceServerConfiguration>> violations = validator.validate(value.getServer());
				if (!violations.isEmpty()) {
					context.disableDefaultConstraintViolation();
					for (ConstraintViolation<DataSourceServerConfiguration> v : violations) {
						context.buildConstraintViolationWithTemplate(v.getMessage())
						       .addPropertyNode("server")
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
