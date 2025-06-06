package de.kiaim.cinnamon.model.validation;

import de.kiaim.cinnamon.model.enumeration.DataType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that a data type is not {@link DataType#UNDEFINED}.
 *
 * @author Daniel Preciado-Marquez
 */
public class DataTypeNotUndefinedValidator implements ConstraintValidator<DataTypeNotUndefined, DataType> {

	@Override
	public boolean isValid(final DataType value, final ConstraintValidatorContext context) {
		return value != DataType.UNDEFINED;
	}
}
