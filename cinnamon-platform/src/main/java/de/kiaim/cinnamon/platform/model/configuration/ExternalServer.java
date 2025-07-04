package de.kiaim.cinnamon.platform.model.configuration;

import de.kiaim.cinnamon.platform.helper.KiAimConfigurationPostProcessor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class ExternalServer {

	/**
	 * Host name for this application used for requests from other modules.
	 */
	@NotBlank
	private String callbackHost;

	/**
	 * Maximum number of processes that are allowed to run in parallel.
	 */
	@NotNull
	private Integer maxParallelProcess;

	/**
	 * URL of the server for the server.
	 */
	@NotBlank
	private String urlServer;

	/**
	 * Endpoint used for health checks.
	 */
	private String healthEndpoint = "";

	//=========================
	//--- Automatically set ---
	//=========================

	/**
	 * Name of the server. Is automatically set at {@link KiAimConfigurationPostProcessor#assignIndices()}.
	 */
	private String name;

	/**
	 * Endpoints of the server.
	 */
	private List<ExternalEndpoint> endpoints = new ArrayList<>();
}
