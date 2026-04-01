package de.kiaim.cinnamon.model.configuration.algorithms;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Definition of an algorithm provided by the external modules.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Definition of an algorithm provided by the external modules.")
@NoArgsConstructor @AllArgsConstructor
@Getter @Setter
@JsonIgnoreProperties(value = {"configurationGroupDefinition"})
public class AlgorithmDefinition {

	/**
	 * Name for identifying the algorithm.
	 * Same as {@link Algorithm#getName()}.
	 */
	@Schema(description = "Name for identifying the algorithm.", example = "ctgan")
	private String name;

	/**
	 * Version of the algorithm.
	 * Same as {@link Algorithm#getVersion()}.
	 */
	@Schema(description = "Version of the algorithm.", example = "1.0.0")
	private String version;

	/**
	 * Type of the algorithm.
	 * Same as {@link Algorithm#getType()}.
	 */
	@Schema(description = "Type of the algorithm.", example = "tabular")
	private String type;

	/**
	 * Endpoint for starting the algorithm.
	 */
	@Schema(description = "Endpoint for starting the algorithm.", example = "/ctgan")
	@JsonProperty("URL")
	private String url;

	/**
	 * Collects the properties for the ConfigurationGroupDefiniton, defined in the frontend.
	 */
	@JsonAnyGetter @JsonAnySetter
	private final Map<String, JsonNode> configurationGroupDefinition = new HashMap<>();

}
