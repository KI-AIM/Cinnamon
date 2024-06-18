package de.kiaim.platform.model.data.exception;

import de.kiaim.platform.model.TransformationErrorType;

/**
 * Exception thrown if the String value does not match a given pattern
 */
public class StringPatternException extends DataBuildingException {
	@Override
	public TransformationErrorType getTransformationErrorType() {
		return TransformationErrorType.FORMAT_ERROR;
	}
}
