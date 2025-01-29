package de.kiaim.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * General info about a dataset.
 */
@Schema(description = "General info about a dataset.")
@Getter @AllArgsConstructor
public class DataSetInfo {

	/**
	 * Number of rows inside the dataset.
	 */
	@Schema(description = "Number of rows inside the dataset.", example = "583")
	private final int numberRows;

	/**
	 * Number of invalid rows inside the dataset.
	 */
	@Schema(description = "Number of invalid rows inside the dataset.", example = "36")
	private final int numberInvalidRows;

	/**
	 * If the data set contains a hold out split.
	 */
	@Schema(description = "If the data set contains a hold out split.", example = "false")
	private final boolean hasHoldOutSplit;
}
