package de.kiaim.cinnamon.model.dto;

import de.kiaim.cinnamon.model.enumeration.AttributeMatchOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * Result of the attribute matching process for a single attribute.
 * Either only the index or the name is set, depending on the import configuration.
 * Based on the value of {@link #getOutcome()}, * the attribute refers to either an attribute in the dataset, in the
 * data configuration, or in both.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Result of the attribute matching process for a single attribute. Either only the index or the name is set, depending on the import configuration. Based on the value of outcome, the attribute refers to either an attribute in the dataset, in the data configuration, or in both.")
@AllArgsConstructor
@Getter
public class AttributeMatchingResult {

	/**
	 * The index of the attribute in the corresponding source.
	 * Can be null if the name is used for matching.
	 */
	@Schema(description = "The index of the attribute in the corresponding source. Can be null if the name is used for matching.")
	@Nullable
	private final Integer index;

	/**
	 * The name of the attribute in the corresponding source.
	 * Can be null if the index is used for matching.
	 */
	@Nullable
	@Schema(description = "The name of the attribute in the corresponding source. Can be null if the index is used for matching.")
	private final String name;

	/**
	 * The outcome of the attribute matching process.
	 */
	@Schema(description = "The outcome of the attribute matching process.")
	private final AttributeMatchOutcome outcome;
}
