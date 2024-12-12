package de.kiaim.platform.service;

import de.kiaim.platform.model.configuration.*;
import de.kiaim.platform.exception.BadStepNameException;
import de.kiaim.platform.exception.InternalApplicationConfigurationException;
import de.kiaim.platform.model.enumeration.Step;
import org.springframework.stereotype.Service;

/**
 * Service for steps.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class StepService {

	private final KiAimConfiguration kiAimConfiguration;

	public StepService(KiAimConfiguration kiAimConfiguration) {
		this.kiAimConfiguration = kiAimConfiguration;
	}

	/**
	 * Returns the configuration for the given step.
	 * @param step The step.
	 * @return The step configuration.
	 * @throws InternalApplicationConfigurationException If no configuration could be found.
	 */
	public StepConfiguration getStepConfiguration(final Step step) throws InternalApplicationConfigurationException {
		if (!kiAimConfiguration.getSteps().containsKey(step)) {
			throw new InternalApplicationConfigurationException(
					InternalApplicationConfigurationException.MISSING_STEP_CONFIGURATION,
					"No configuration for the step '" + step.name() + "' found!");
		}

		return kiAimConfiguration.getSteps().get(step);
	}

	public ExternalEndpoint getExternalServerEndpointConfiguration(
			final StepConfiguration stepConfiguration) {
		return kiAimConfiguration.getExternalServerEndpoints().get(stepConfiguration.getExternalServerEndpointIndex());
	}

	public ExternalEndpoint getExternalServerEndpointConfiguration(
			final Step step) throws InternalApplicationConfigurationException {
		final StepConfiguration stepConfiguration = getStepConfiguration(step);
		return kiAimConfiguration.getExternalServerEndpoints().get(stepConfiguration.getExternalServerEndpointIndex());
	}

	public ExternalServer getExternalServerConfiguration(final ExternalEndpoint externalServerEndpoint) {
		return kiAimConfiguration.getExternalServer().get(externalServerEndpoint.getExternalServerIndex());
	}

	public ExternalServer getExternalServerConfiguration(final Step step ) throws InternalApplicationConfigurationException {
		final var ese = getExternalServerEndpointConfiguration(step);
		return kiAimConfiguration.getExternalServer().get(ese.getExternalServerIndex());
	}

	/**
	 * Returns the configuration for the step with the given name.
	 * @param stepName The name of the step.
	 * @return The step configuration.
	 * @throws BadStepNameException If no configuration could be found.
	 */
	public StepConfiguration getStepConfiguration(final String stepName) throws BadStepNameException {
		try {
			return getStepConfiguration(Step.getStepOrThrow(stepName));
		} catch (final InternalApplicationConfigurationException e) {
			throw new BadStepNameException(BadStepNameException.NOT_FOUND,
			                               "The step '" + stepName + "' is not defined!");
		}
	}

	/**
	 * Returns the configuration for the given stage.
	 *
	 * @param stage The stage.
	 * @return The step configuration.
	 * @throws InternalApplicationConfigurationException If no configuration could be found.
	 */
	public StageConfiguration getStageConfiguration(final Step stage) throws InternalApplicationConfigurationException {
		if (!kiAimConfiguration.getStages().containsKey(stage)) {
			throw new InternalApplicationConfigurationException(
					InternalApplicationConfigurationException.MISSING_STEP_CONFIGURATION,
					"No configuration for the stage '" + stage.name() + "' found!");
		}

		return kiAimConfiguration.getStages().get(stage);
	}

	/**
	 * Returns the configuration for the stage with the given name.
	 *
	 * @param stageName The name of the stage.
	 * @return The step configuration.
	 * @throws BadStepNameException If no configuration could be found.
	 */
	public StageConfiguration getStageConfiguration(final String stageName) throws BadStepNameException {
		try {
			return getStageConfiguration(Step.getStepOrThrow(stageName));
		} catch (final InternalApplicationConfigurationException e) {
			throw new BadStepNameException(BadStepNameException.NOT_FOUND,
			                               "The step '" + stageName + "' is not defined!");
		}
	}
}
