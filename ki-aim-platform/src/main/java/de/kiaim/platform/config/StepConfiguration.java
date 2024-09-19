package de.kiaim.platform.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

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
	 * Name of the configuration.
	 */
	@NotBlank
	private String configurationName;

	/**
	 * Maximum number of processes that are allowed to run in parallel.
	 */
	@JsonIgnore
	@NotNull
	private Integer maxParallelProcess;

	/**
	 * List of required pre-processors for this step.
	 */
	@JsonIgnore
	private List<String> preProcessors;

	/**
	 * Endpoint for retrieving the status.
	 */
	@NotBlank
	private String statusEndpoint;

	/**
	 * URL of the server.
	 */
	@Schema(description = "URL of the corresponding server.", example = "https://my-anonymization-server.de")
	@NotBlank
	private String url;

	/**
	 * URL of the server for clients.
	 */
	@Schema(description = "URL of the corresponding server for the client.",
	        example = "https://my-anonymization-server.de")
	@NotBlank
	private String urlClient;
}
