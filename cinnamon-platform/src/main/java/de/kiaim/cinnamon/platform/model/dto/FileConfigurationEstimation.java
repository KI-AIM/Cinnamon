package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.model.configuration.data.file.FileConfiguration;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Result of the file configuration estimation.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Result of the file configuration estimation.")
@AllArgsConstructor
@Getter
public class FileConfigurationEstimation {

	/**
	 * The estimated file configuration.
	 */
	@Schema(description = "The estimated file configuration.")
	private final FileConfiguration estimation;

}
