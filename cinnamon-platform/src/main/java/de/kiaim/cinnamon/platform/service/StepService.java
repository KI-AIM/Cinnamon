package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.platform.exception.BadConfigurationNameException;
import de.kiaim.cinnamon.platform.model.configuration.*;
import de.kiaim.cinnamon.platform.exception.BadStepNameException;
import de.kiaim.cinnamon.platform.model.entity.BackgroundProcessEntity;
import de.kiaim.cinnamon.platform.model.entity.ExternalProcessEntity;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.enumeration.DataSetSelector;
import org.springframework.stereotype.Service;

/**
 * Service for steps.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class StepService {

	private final CinnamonConfiguration cinnamonConfiguration;

	public StepService(CinnamonConfiguration cinnamonConfiguration) {
		this.cinnamonConfiguration = cinnamonConfiguration;
	}

	public ExternalEndpoint getExternalServerEndpointConfiguration(final Job stepConfiguration) {
		return cinnamonConfiguration.getExternalServerEndpoints().get(stepConfiguration.getExternalServerEndpointIndex());
	}

	public ExternalEndpoint getExternalServerEndpointConfiguration(final BackgroundProcessEntity process) {
		return cinnamonConfiguration.getExternalServerEndpoints().get(process.getEndpoint());
	}

	public ExternalServer getExternalServerConfiguration(final ExternalEndpoint externalServerEndpoint) {
		return cinnamonConfiguration.getExternalServer().get(externalServerEndpoint.getExternalServerName());
	}

	/**
	 * Returns the configuration for the step with the given name.
	 * @param stepName The name of the step.
	 * @return The step configuration.
	 * @throws BadStepNameException If no configuration could be found.
	 */
	public Job getStepConfiguration(final String stepName) throws BadStepNameException {
		if (!cinnamonConfiguration.getSteps().containsKey(stepName.toLowerCase())) {
			throw new BadStepNameException(BadStepNameException.NOT_FOUND,
			                               "The job '" + stepName + "' is not defined!");
		}

		return cinnamonConfiguration.getSteps().get(stepName.toLowerCase());
	}

	/**
	 * Returns the configuration for the given stage.
	 *
	 * @param stageName The stage.
	 * @return The step configuration.
	 * @throws BadStepNameException If no configuration could be found.
	 */
	public Stage getStageConfiguration(final String stageName) throws BadStepNameException {
		if (!cinnamonConfiguration.getStages().containsKey(stageName.toLowerCase())) {
			throw new BadStepNameException(BadStepNameException.NOT_FOUND,
			                               "The step '" + stageName + "' is not defined!");
		}

		return cinnamonConfiguration.getStages().get(stageName.toLowerCase());
	}

	public ExternalConfiguration getExternalConfiguration(final String configurationName) throws BadConfigurationNameException {
		if (!cinnamonConfiguration.getExternalConfiguration().containsKey(configurationName.toLowerCase())) {
			throw new BadConfigurationNameException(BadConfigurationNameException.NOT_FOUND,
			                                        "No configuration with name '" + configurationName +
			                                        "' registered!");
		}

		return cinnamonConfiguration.getExternalConfiguration().get(configurationName.toLowerCase());
	}

	public ExternalProcessEntity getProcess(final String jobName, final ProjectEntity project) throws BadStepNameException {
		ExternalProcessEntity process = null;

		for (final var stage : project.getPipelines().get(0).getStages()) {
			for (final var job : stage.getProcesses()) {
				if (job.getJob().getName().equalsIgnoreCase(jobName)) {
					process = job;
					break;
				}
			}
		}

		if (process == null) {
			throw new BadStepNameException(BadStepNameException.NOT_FOUND,
			                               "The job '" + jobName + "' is not defined!");
		}

		return process;
	}

	/**
	 * Checks if the given job requires a hold-out split to be present.
	 *
	 * @param job The job.
	 * @return If the job requires a hold-out split.
	 */
	public boolean requiresHoldOutSplit(final Job job) {
		return job.getEndpoint().getInputs()
		          .stream()
		          .anyMatch(input -> input.getSelector().equals(DataSetSelector.HOLD_OUT));
	}
}
