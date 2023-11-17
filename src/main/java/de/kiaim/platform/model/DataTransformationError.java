package de.kiaim.platform.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents an error during the transformation.
 */
@Getter
@AllArgsConstructor
public class DataTransformationError {

	/**
	 * Index of the corresponding value in the list of raw values.
	 */
	private final int index;

	/**
	 * Type of the error.
	 */
	private final TransformationErrorType errorType;
}
