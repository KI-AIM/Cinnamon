package de.kiaim.platform.model.configuration;

import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.model.enumeration.StepType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for a sigle step.
 */
@Schema(description = "Configuration of a step.")
@Getter @Setter
public class StepConfiguration {

	/**
	 * Endpoint to be used for this step.
	 */
	private Integer externalServerEndpointIndex;

	/**
	 * Type of this step.
	 */
	private StepType stepType;

	//=========================
	//--- Automatically set ---
	//=========================

	private Step step;
	private ExternalEndpoint endpoint;

	public ExternalServer getServer() {
		return endpoint.getServer();
	}
}
