package de.kiaim.model.exception;

import de.kiaim.model.enumeration.TransformationErrorType;

/**
 * Exception thrown if the date does not have a valid format.
 */
public class DateFormatException extends DataBuildingException {
	@Override
	public TransformationErrorType getTransformationErrorType() {
		return TransformationErrorType.FORMAT_ERROR;
	}
}
