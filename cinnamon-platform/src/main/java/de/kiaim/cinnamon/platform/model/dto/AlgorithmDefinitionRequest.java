package de.kiaim.cinnamon.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "Request for fetching a configuration definition.")
@Data
public class AlgorithmDefinitionRequest {

	@Schema(description = "Name of the configuration.", example = "synthetization_configuration")
	@NotBlank
	private String configurationName;

	@Schema(description = "Path to the configuration definition.", example = "/api/anonymization/anon-tabular-privacy-model-config")
	@NotBlank
	private String definitionPath;
}
