package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * Attribute that is only present in the data and is missing a configuration.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Attribute that is only present in the data and is missing a configuration.")
@AllArgsConstructor
@Getter
public class MissingAttribute {

	/**
	 * The index of the attribute in the data.
	 */
	@Schema(description = "The index of the attribute in the data.")
	private final int index;

	/**
	 * The name of the attribute in the data. Empty if the data has no header.
	 */
	@Schema(description = "The name of the attribute in the data. Empty if the data has no header.")
	private final String name;

	/**
	 * Estimated configuration for the attribute.
	 * Null if the estimation was not possible.
	 */
	@Schema(description = "Estimated configuration for the attribute. Null if the estimation was not possible.")
	@Nullable
	private final ColumnConfiguration suggestedConfiguration;

	/**
	 * Confidence of the estimation between 0 and 1.
	 * Null if the estimation was not possible.
	 */
	@Schema(description = "Confidence of the estimation between 0 and 1. Null if the estimation was not possible.")
	@Nullable
	private final Float confidence;
}
