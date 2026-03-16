package de.kiaim.cinnamon.model.validation;

import de.kiaim.cinnamon.model.configuration.data.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.DateFormatConfiguration;
import de.kiaim.cinnamon.model.configuration.data.DateTimeFormatConfiguration;
import de.kiaim.cinnamon.model.enumeration.DataType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that an attribute of type DATE or DATE_TIME has a format configuration.
 * Can be used with {@link DateFormatProvidedConstraint}.
 *
 * @author Daniel Preciado-Marquez
 */
public class DateFormatProvidedValidator implements ConstraintValidator<DateFormatProvidedConstraint, ColumnConfiguration> {

	@Override
	public boolean isValid(final ColumnConfiguration value, final ConstraintValidatorContext context) {
		if (value.getType() == null) {
			return true;
		}

		if (value.getType() == DataType.DATE) {
			final var format = value.getConfiguration(DateFormatConfiguration.class);
			context.buildConstraintViolationWithTemplate("The format must be provided for Date attributes!")
			       .addPropertyNode("configurations")
			       .addConstraintViolation()
			       .disableDefaultConstraintViolation();
			return format != null;
		}

		if (value.getType() == DataType.DATE_TIME) {
			final var format = value.getConfiguration(DateTimeFormatConfiguration.class);
			context.buildConstraintViolationWithTemplate("The format must be provided for Date & Time attributes!")
			       .addPropertyNode("configurations")
			       .addConstraintViolation()
			       .disableDefaultConstraintViolation();
			return format != null;
		}

		return true;
	}
}
