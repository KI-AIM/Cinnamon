package de.kiaim.platform.model.data.exception;

import de.kiaim.platform.model.TransformationErrorType;

/**
 * Exception thrown if the date does not have a valid format
 */
public class DateFormatException extends DataBuildingException {
	@Override
	public TransformationErrorType getTransformationErrorType() {
		return TransformationErrorType.FORMAT_ERROR;
	}
}
