package de.kiaim.platform.model.data.exception;

import de.kiaim.platform.model.TransformationErrorType;

/**
 * Exception thrown if the String value cannot be parsed as an Integer
 */
public class IntFormatException extends  DataBuildingException {
	@Override
	public TransformationErrorType getTransformationErrorType() {
		return TransformationErrorType.FORMAT_ERROR;
	}
}
