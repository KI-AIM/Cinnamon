package de.kiaim.cinnamon.platform.model.configuration;

import de.kiaim.cinnamon.platform.model.enumeration.StepType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for a sigle step.
 */
@Schema(description = "Configuration of a step.")
@Getter @Setter
public class Job {

	/**
	 * Endpoint to be used for this step.
	 */
	private Integer externalServerEndpointIndex;

	/**
	 * Hardcoded fix for synth status.
	 */
	boolean fixStatus = false;

	/**
	 * Type of this step.
	 */
	private StepType stepType;

	//=========================
	//--- Automatically set ---
	//=========================

	private String name;

	private ExternalEndpoint endpoint;

	public ExternalServer getServer() {
		return endpoint.getServer();
	}
}
