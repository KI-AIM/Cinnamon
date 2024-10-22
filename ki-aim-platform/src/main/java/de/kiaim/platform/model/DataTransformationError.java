package de.kiaim.platform.model;

import de.kiaim.model.enumeration.TransformationErrorType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents an error during the transformation.
 */
@Schema(description = "Represents an error during the transformation of a single value.")
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class DataTransformationError {

	/**
	 * Index of the corresponding value in the list of raw values.
	 */
	@Schema(description = "Index of the value in the row.", example = "2")
	private final int index;

	/**
	 * Type of the error.
	 */
	@Schema(description = "Type of the error.", example = "MISSING_VALUE")
	private final TransformationErrorType errorType;

	/**
	 * Raw value from the data set.
	 */
	@Schema(description = "Raw value from the data set.", example = "2023-12-24")
	private final String rawValue;
}
