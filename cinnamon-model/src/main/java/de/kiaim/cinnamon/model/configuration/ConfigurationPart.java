package de.kiaim.cinnamon.model.configuration;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import de.kiaim.cinnamon.model.configuration.algorithms.AlgorithmSelector;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Class that represents a configuration for an external module.
 *
 * @author Daniel Preciado-Marquez
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@NoArgsConstructor
@Getter @Setter
public class ConfigurationPart {

	/**
	 * Algorithm to be used.
	 * Can be null or incomplete when importing older configurations.
	 */
	@Schema(description = "Algorithm to be used.")
	@Nullable @Valid
	private AlgorithmSelector algorithm = null;

	/**
	 * Collection of parameters as specified by the external module.
	 */
	@Schema(description = "Collection of parameters as specified by the external module.")
	@JsonAnyGetter @JsonAnySetter @JsonIgnore
	private Map<String, JsonNode> configuration = new HashMap<>();
}
