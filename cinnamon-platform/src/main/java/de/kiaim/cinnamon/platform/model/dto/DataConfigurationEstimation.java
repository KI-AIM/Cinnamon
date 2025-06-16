package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Result of the data configuration estimation.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Result of the data configuration estimation.")
@Data
public class DataConfigurationEstimation {
	/**
	 * The estimated data configuration.
	 */
	@Schema(description = "The estimated data configuration.")
	private final DataConfiguration dataConfiguration;

	/**
	 * The confidences of the estimation for each column ordered by the attribute index.
	 * Values are between 0 (small confidence) and 1 (high confidence).
	 */
	@Schema(description = "The confidences of the estimation for each column. Values are between 0 (small confidence) and 1 (high confidence)",
	        example = "[0.9, 1, 0.66, 1.0, 1.0, 1.0]")
	private final float[] confidences;
}
