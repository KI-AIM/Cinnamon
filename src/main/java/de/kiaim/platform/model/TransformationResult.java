package de.kiaim.platform.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * Represents the result of the transformation from the raw input data into DataSet.
 */
@Schema(description = "Result of the transformation containing all valid rows and occurred errors.")
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class TransformationResult {

	/**
	 * The transformed DataSet containing all successful transformed entries.
	 */
	@Schema(description = "Transformed data containing all valid rows.")
	private final DataSet dataSet;

	/**
	 * List of errors containing all rows that could not be transformed.
	 */
	@Schema(description = "Transformed data containing all valid rows.")
	private final List<DataRowTransformationError> transformationErrors;

	/**
	 * Adds a new row the list of rows that could not be transformed.
	 * @param dataRowTransformationError Error containing the row.
	 */
	public void addError(final DataRowTransformationError dataRowTransformationError) {
		transformationErrors.add(dataRowTransformationError);
	}
}
