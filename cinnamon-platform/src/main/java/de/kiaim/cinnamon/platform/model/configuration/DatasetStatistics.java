package de.kiaim.cinnamon.platform.model.configuration;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Defines statistics that can be calculated for a dataset.
 *
 * @author Daniel Preciado-Marquez
 */
@Getter @Setter
public class DatasetStatistics {

	/**
	 * Key for identifying and accessing the statistics.
	 */
	@NotBlank
	private String key;

	/**
	 * The key defined in {@link CinnamonConfiguration#getExternalServerEndpoints()} of the endpoint used for calculating the statistics.
	 */
	private int endpoint;

	/**
	 * Name of the file returned by the statistics endpoint containing the statistics.
	 */
	@NotBlank
	private String fileName;
}
