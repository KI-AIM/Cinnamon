package de.kiaim.platform.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Class for storing an entry of the original input data that could not be transformed successfully.
 */
@Getter
@AllArgsConstructor
public class DataRowTransformationError {

	/**
	 * Original index of the row in the input data.
	 */
	private final int index;

	/**
	 * List of all values represented as Strings.
	 */
	private final List<String> rawValues;

	/**
	 * List of errors that occurred during the transformation.
	 */
	private final List<DataTransformationError> dataTransformationErrors = new ArrayList<>();

	/**
	 * Adds a new error to list of errors.
	 * @param dataTransformationError The error to be added.
	 */
	public void addError(final DataTransformationError dataTransformationError) {
		dataTransformationErrors.add(dataTransformationError);
	}
}
