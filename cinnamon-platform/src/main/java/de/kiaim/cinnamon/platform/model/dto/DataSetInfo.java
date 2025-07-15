package de.kiaim.cinnamon.platform.model.dto;

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
	@Schema(description = "If the data set contains a hold out split.", example = "true")
	private final boolean hasHoldOutSplit;

	/**
	 * The percentage of rows that are assigned to the hold-out split.
	 */
	@Schema(description = "The percentage of rows that are assigned to the hold-out split.", example = "0.2")
	private final float holdOutPercentage;

	/**
	 * Number of rows inside the hold-out split.
	 */
	@Schema(description = "The number of rows that are assigned to the hold-out split.", example = "116")
	private final int numberHoldOutRows;

	/**
	 * Number of invalid rows inside the hold-out split.
	 */
	@Schema(description = "The number of invalid rows inside the hold-out split.", example = "8")
	private final int numberInvalidHoldOutRows;

	/**
	 * Information about the corresponding data configuration.
	 */
	private final DataConfigurationInfo dataConfigurationInfo;
}
