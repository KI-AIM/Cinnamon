package de.kiaim.cinnamon.model.configuration.algorithms;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * Definition of an algorithm provided by the external modules.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Definition of an algorithm provided by the external modules.")
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor @AllArgsConstructor
@Getter @Setter
@EqualsAndHashCode
public class Algorithm {
	/**
	 * Name for identifying the algorithm.
	 * Same as {@link AlgorithmDefinition#getName()}.
	 */
	@Schema(description = "Name for identifying the algorithm.", example = "ctgan")
	private String name;

	/**
	 * Human-readable name of the algorithm.
	 */
	@Schema(description = "Human-readable name of the algorithm.", example = "Conditional GAN")
	@JsonProperty("display_name")
	private String displayName;

	/**
	 * Human-readable description of the algorithm.
	 */
	@Schema(description = "Human-readable description of the algorithm.", example = "Conditional GAN")
	private String description;

	// TODO enum?
	/**
	 * Type of the algorithm.
	 * Same as {@link AlgorithmDefinition#getType()}.
	 */
	@Schema(description = "Type of the algorithm.", example = "tabular")
	private String type;

	/**
	 * Version of the algorithm.
	 * Same as {@link AlgorithmDefinition#getVersion()}.
	 */
	@Schema(description = "Version of the algorithm.", example = "1.0.0")
	private String version;

	/**
	 * Endpoint for fetching the algorithm definition.
	 */
	@Schema(description = "Endpoint for fetching the algorithm definition.", example = "/ctgan")
	@JsonProperty("URL")
	private String url;
}
