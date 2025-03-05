package de.kiaim.cinnamon.model.exception;

import de.kiaim.cinnamon.model.enumeration.TransformationErrorType;

/**
 * Exception thrown if the date-time does not have a valid format.
 */
public class DateTimeFormatException extends DataBuildingException {
	@Override
	public TransformationErrorType getTransformationErrorType() {
		return TransformationErrorType.FORMAT_ERROR;
	}
}
