package de.kiaim.platform.model.data.exception;

import de.kiaim.platform.model.TransformationErrorType;

public class ValueNotInRangeException extends DataBuildingException {
	@Override
	public TransformationErrorType getTransformationErrorType() {
		return TransformationErrorType.VALUE_NOT_IN_RANGE;
	}
}
