package de.kiaim.cinnamon.model.configuration.algorithms;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

/**
 * List of algorithms provided by an external module.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "List of algorithms provided by an external module.")
@NoArgsConstructor @AllArgsConstructor
@Getter @Setter
@EqualsAndHashCode
public class AvailableAlgorithms {
	/**
	 * List of algorithms provided by an external module.
	 */
	@Schema(description = "List of algorithms provided by an external module.")
	private List<Algorithm> algorithms;
}
