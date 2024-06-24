package de.kiaim.model.exception;

import de.kiaim.model.enumeration.TransformationErrorType;

/**
 * Base exception for all exceptions that occur during building a Data object.
 */
public abstract class DataBuildingException extends Exception {
	public abstract TransformationErrorType getTransformationErrorType();
}
