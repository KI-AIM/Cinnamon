package de.kiaim.cinnamon.model.exception;

import de.kiaim.cinnamon.model.enumeration.TransformationErrorType;

/**
 * Exception thrown if an empty value is parsed.
 */
public class MissingValueException extends DataBuildingException {
	@Override
	public TransformationErrorType getTransformationErrorType() {
		return TransformationErrorType.MISSING_VALUE;
	}
}
