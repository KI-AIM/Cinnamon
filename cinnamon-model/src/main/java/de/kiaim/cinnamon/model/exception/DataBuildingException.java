package de.kiaim.cinnamon.model.exception;

import de.kiaim.cinnamon.model.enumeration.TransformationErrorType;

/**
 * Base exception for all exceptions that occur during building a Data object.
 */
public abstract class DataBuildingException extends Exception {
	public abstract TransformationErrorType getTransformationErrorType();
}
