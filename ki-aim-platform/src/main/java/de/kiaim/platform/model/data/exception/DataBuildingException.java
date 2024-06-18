package de.kiaim.platform.model.data.exception;

import de.kiaim.platform.model.TransformationErrorType;

/**
 * Base exception for all exceptions that occur during building a Data object.
 */
public abstract class DataBuildingException extends Exception {
	public abstract TransformationErrorType getTransformationErrorType();
}
