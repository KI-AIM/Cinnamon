package de.kiaim.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Schema(description = "Request for starting an external process.")
@Getter @Setter
public class StartProcessRequest {

	@Schema(description = "Name of the step.", example = "synthetization")
	@NotBlank
	private String stepName;

	@Schema(description = "Name of the algorithm to start.", example = "ctgan")
	@NotBlank
	private String algorithm;

	private String url;

	@Schema(description = "Name under which the configuration should be saved.", example = "synthetization-config")
	@NotBlank
	private String configurationName;

	@Schema(description = "Process specific configuration.")
	@NotBlank
	private String configuration;
}
