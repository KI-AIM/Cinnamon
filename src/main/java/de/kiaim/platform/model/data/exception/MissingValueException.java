package de.kiaim.platform.model.data.exception;

import de.kiaim.platform.model.TransformationErrorType;

public class MissingValueException extends DataBuildingException {
	@Override
	public TransformationErrorType getTransformationErrorType() {
		return TransformationErrorType.MISSING_VALUE;
	}
}
