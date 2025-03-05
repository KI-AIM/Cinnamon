package de.kiaim.cinnamon.model.exception;

import de.kiaim.cinnamon.model.enumeration.TransformationErrorType;

/**
 * Exception thrown if the String value cannot be parsed as an Integer.
 */
public class IntFormatException extends  DataBuildingException {
	@Override
	public TransformationErrorType getTransformationErrorType() {
		return TransformationErrorType.FORMAT_ERROR;
	}
}
