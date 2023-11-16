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
	 * Message describing the error.
	 */
	private final String errorMessage;
}
