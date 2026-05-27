package de.kiaim.cinnamon.model.configuration.algorithms;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Specifies of the selected algorithm.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Specifies of the selected algorithm.")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@NoArgsConstructor
@Getter @Setter
public class AlgorithmSelector {

	/**
	 * ID of the algorithm.
	 */
	@Schema(description = "ID of the algorithm.", example = "anonymization")
	@NotBlank
	private String id;

	/**
	 * Version of the algorithm.
	 */
	@Schema(description = "Version of the algorithm.", example = "1.0.0")
	@NotBlank
	private String version;

	/**
	 * Additional parameters for the algorithm used by the external modules.
	 */
	@Schema(description = "Collection of parameters as specified by the external module.")
	@JsonAnyGetter @JsonAnySetter @JsonIgnore
	private Map<String, JsonNode> configuration = new HashMap<>();
}
