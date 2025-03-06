package de.kiaim.cinnamon.platform.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Class for storing an entry of the original input data that could not be transformed successfully.
 */
@Schema(description = "Represents a invalid row in the data set.")
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class DataRowTransformationError {

	public DataRowTransformationError(int index) {
		this.index = index;
		this.dataTransformationErrors = new ArrayList<>();
	}

	/**
	 * Original index of the row in the input data.
	 */
	@Schema(description = "Index of the row in the original data.", example = "1")
	private final int index;

	/**
	 * List of errors that occurred during the transformation.
	 */
	@Schema(description = "List of errors that occurred during the transformation.")
	private final List<DataTransformationError> dataTransformationErrors;

	/**
	 * Adds a new error to list of errors.
	 * @param dataTransformationError The error to be added.
	 */
	public void addError(final DataTransformationError dataTransformationError) {
		dataTransformationErrors.add(dataTransformationError);
	}
}
