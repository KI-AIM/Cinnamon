package de.kiaim.cinnamon.platform.model.configuration;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration object for configurations that are created based on the definition of an external server
 * and used for starting jobs.
 *
 * @author Daniel Preciado-Marquez
 */
@Getter @Setter
public class ExternalConfiguration {

	/**
	 * Endpoint for fetching the available algorithms.
	 */
	@NotBlank
	private String algorithmEndpoint;

	/**
	 * Index of the server.
	 */
	@NotBlank
	private String externalServerName;

	//=========================
	//--- Automatically set ---
	//=========================

	/**
	 * Name of the configuration.
	 */
	private String configurationName;

	/**
	 * Server used for fetching the config definition.
	 */
	private ExternalServer externalServer;

	/**
	 * Endpoints that are using this configuration.
	 * Mapping for {@link ExternalEndpoint#getConfiguration()}.
	 */
	private final List<ExternalEndpoint> usages = new ArrayList<>();
}
