package de.kiaim.model.exception;

import de.kiaim.model.enumeration.TransformationErrorType;

/**
 * Exception thrown if the String value does not match a given pattern.
 */
public class StringPatternException extends DataBuildingException {
	@Override
	public TransformationErrorType getTransformationErrorType() {
		return TransformationErrorType.FORMAT_ERROR;
	}
}
