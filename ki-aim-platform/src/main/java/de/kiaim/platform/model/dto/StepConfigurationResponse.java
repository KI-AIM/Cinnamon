package de.kiaim.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Schema(description = "Configuration of a step.")
@Getter @Setter
public class StepConfigurationResponse {
	/**
	 * Endpoint for fetching the available algorithms.
	 */
	@Schema(description = "Endpoint for fetching the available algorithms.", example = "/anonymization/algorithms")
	private String algorithmEndpoint;

	/**
	 * Name of the configuration.
	 */
	@Schema(description = "Name of the configuration.", example = "anonConfig")
	private String configurationName;

	/**
	 * URL of the server for clients.
	 */
	@Schema(description = "URL of the corresponding server for the client.",
	        example = "https://my-anonymization-server.de")
	private String urlClient;
}
