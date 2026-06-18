package de.kiaim.cinnamon.model.validation;

import de.kiaim.cinnamon.model.configuration.data.DataSourceConfiguration;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that the data source configuration is valid for the selected data source type.
 * <br>
 * Can be used with {@link DataSourceConfigured}.
 *
 * @author Daniel Preciado-Marquez
 */
public class DataSourceConfiguredValidator implements ConstraintValidator<DataSourceConfigured, DataSourceConfiguration> {

	@Override
	public boolean isValid(final DataSourceConfiguration value, final ConstraintValidatorContext context) {
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
		}

		return true;
	}
}
