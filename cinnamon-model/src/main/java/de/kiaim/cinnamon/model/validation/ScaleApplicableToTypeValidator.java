package de.kiaim.cinnamon.model.validation;

import de.kiaim.cinnamon.model.configuration.data.ColumnConfiguration;
import de.kiaim.cinnamon.model.enumeration.DataScale;
import de.kiaim.cinnamon.model.enumeration.DataType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that the scale is applicable to the type of the attribute.
 * Null values and {@link DataType#UNDEFINED} data type are considered as valid.
 * Can be used with {@link ScaleApplicableToType}.
 *
 * @author Daniel Preciado-Marquez
 */
public class ScaleApplicableToTypeValidator implements ConstraintValidator<ScaleApplicableToType, ColumnConfiguration> {

	@Override
	public boolean isValid(final ColumnConfiguration value, final ConstraintValidatorContext context) {
		final DataType type = value.getType();
		final DataScale scale = value.getScale();

		if (type == null || scale == null || type == DataType.UNDEFINED) {
			return true;
		}

		return type.getSupportedScales().contains(scale);
	}
}
