package de.kiaim.platform.model.validation;

import de.kiaim.platform.model.dto.ConfigureProcessRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that if the step should not be skipped, all necessary data is present.
 *
 * @author Daniel Preciado-Marquez
 */
public class ValidConfigurationValidator implements ConstraintValidator<ValidConfiguration, ConfigureProcessRequest> {
	@Override
	public boolean isValid(final ConfigureProcessRequest value, final ConstraintValidatorContext context) {
		if (value.isSkip()) {
			return true;
		}

		boolean isValid = true;
		if (value.getUrl() == null || value.getUrl().isEmpty()) {
			context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
			       .addPropertyNode("url").addConstraintViolation();
			isValid = false;
		}

		if (value.getConfiguration() == null || value.getConfiguration().isEmpty()) {
			context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
			       .addPropertyNode("configuration").addConstraintViolation();
			isValid = false;
		}

		return isValid;
	}
}
