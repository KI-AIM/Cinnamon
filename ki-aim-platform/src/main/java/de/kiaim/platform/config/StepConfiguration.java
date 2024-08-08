package de.kiaim.platform.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for a sigle step.
 */
@Schema(description = "Configuration of a step.")
@Getter @Setter
public class StepConfiguration {

	/**
	 * Endpoint for fetching the available algorithms.
	 */
	@NotBlank
	private String algorithmEndpoint;

	/**
	 * Host name for this application used for requests from other modules.
	 */
	@JsonIgnore
	@NotBlank
	private String callbackHost;

	/**
	 * Endpoint for cancelling requests.
	 */
	@JsonIgnore
	@NotBlank
	private String cancelEndpoint;

	/**
	 * URL of the server.
	 */
	@Schema(description = "URL of the corresponding server.", example = "https://my-anonymization-server.de")
	@NotBlank
	private String url;

}
