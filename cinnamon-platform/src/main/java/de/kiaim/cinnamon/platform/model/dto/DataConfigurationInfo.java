package de.kiaim.cinnamon.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DTO for general information about a data configuration.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "General information about a data configuration.")
@Getter @AllArgsConstructor
public class DataConfigurationInfo {

	/**
	 * Number of columns in the data set.
	 */
	@Schema(description = "Number of columns in the data set.", example = "10")
	private final int numberColumns;

	/**
	 * Number of numeric columns in the data set.
	 */
	@Schema(description = "Number of numeric columns in the data set.", example = "5")
	private final int numberNumericColumns;

	/**
	 * Number of categorical columns in the data set.
	 */
	@Schema(description = "Number of categorical columns in the data set.", example = "3")
	private final int numberCategoricalColumns;

	/**
	 * Number of date columns in the data set.
	 */
	@Schema(description = "Number of date columns in the data set.", example = "2")
	private final int numberDateColumns;
}
