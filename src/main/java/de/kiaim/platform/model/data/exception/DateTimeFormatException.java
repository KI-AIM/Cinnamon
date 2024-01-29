package de.kiaim.platform.model.data.exception;

import de.kiaim.platform.model.TransformationErrorType;

/**
 * Exception thrown if the date-time does not have a valid format
 */
public class DateTimeFormatException extends DataBuildingException {
	@Override
	public TransformationErrorType getTransformationErrorType() {
		return TransformationErrorType.FORMAT_ERROR;
	}
}
