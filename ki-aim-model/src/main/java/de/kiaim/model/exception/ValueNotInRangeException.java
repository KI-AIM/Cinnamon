package de.kiaim.model.exception;

import de.kiaim.model.enumeration.TransformationErrorType;

/**
 * Exception thrown if the parsed value is not in the configured range.
 */
public class ValueNotInRangeException extends DataBuildingException {
	@Override
	public TransformationErrorType getTransformationErrorType() {
		return TransformationErrorType.VALUE_NOT_IN_RANGE;
	}
}
