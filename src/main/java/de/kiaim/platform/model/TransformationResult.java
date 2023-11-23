package de.kiaim.platform.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;

/**
 * Represents the result of the transformation from the raw input data into DataSet.
 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class TransformationResult {

	/**
	 * The transformed DataSet containing all successful transformed entries.
	 */
	private final DataSet dataSet;

	/**
	 * List of errors containing all rows that could not be transformed.
	 */
	private final List<DataRowTransformationError> transformationErrors;

	/**
	 * Adds a new row the list of rows that could not be transformed.
	 * @param dataRowTransformationError Error containing the row.
	 */
	public void addError(final DataRowTransformationError dataRowTransformationError) {
		transformationErrors.add(dataRowTransformationError);
	}
}
