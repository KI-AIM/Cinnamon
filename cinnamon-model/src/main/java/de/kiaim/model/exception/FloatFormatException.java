package de.kiaim.model.exception;

import de.kiaim.model.enumeration.TransformationErrorType;

/**
 * Exception thrown if the string value cannot be parsed as float.
 */
public class FloatFormatException extends DataBuildingException {
	@Override
	public TransformationErrorType getTransformationErrorType() {
		return TransformationErrorType.FORMAT_ERROR;
	}
}
