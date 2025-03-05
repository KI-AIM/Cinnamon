package de.kiaim.platform.service;

import de.kiaim.platform.exception.BadConfigurationNameException;
import de.kiaim.platform.model.configuration.*;
import de.kiaim.platform.exception.BadStepNameException;
import de.kiaim.platform.model.entity.ExternalProcessEntity;
import de.kiaim.platform.model.entity.ProjectEntity;
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

	public ExternalEndpoint getExternalServerEndpointConfiguration(final Job stepConfiguration) {
		return kiAimConfiguration.getExternalServerEndpoints().get(stepConfiguration.getExternalServerEndpointIndex());
	}

	public ExternalServer getExternalServerConfiguration(final ExternalEndpoint externalServerEndpoint) {
		return kiAimConfiguration.getExternalServer().get(externalServerEndpoint.getExternalServerIndex());
	}

	/**
	 * Returns the configuration for the step with the given name.
	 * @param stepName The name of the step.
	 * @return The step configuration.
	 * @throws BadStepNameException If no configuration could be found.
	 */
	public Job getStepConfiguration(final String stepName) throws BadStepNameException {
		if (!kiAimConfiguration.getSteps().containsKey(stepName.toLowerCase())) {
			throw new BadStepNameException(BadStepNameException.NOT_FOUND,
			                               "The job '" + stepName + "' is not defined!");
		}

		return kiAimConfiguration.getSteps().get(stepName.toLowerCase());
	}

	/**
	 * Returns the configuration for the given stage.
	 *
	 * @param stageName The stage.
	 * @return The step configuration.
	 * @throws BadStepNameException If no configuration could be found.
	 */
	public Stage getStageConfiguration(final String stageName) throws BadStepNameException {
		if (!kiAimConfiguration.getStages().containsKey(stageName.toLowerCase())) {
			throw new BadStepNameException(BadStepNameException.NOT_FOUND,
			                               "The step '" + stageName + "' is not defined!");
		}

		return kiAimConfiguration.getStages().get(stageName.toLowerCase());
	}

	public ExternalConfiguration getExternalConfiguration(final String configurationName) throws BadConfigurationNameException {
		if (!kiAimConfiguration.getExternalConfiguration().containsKey(configurationName.toLowerCase())) {
			throw new BadConfigurationNameException(BadConfigurationNameException.NOT_FOUND,
			                                        "No configuration with name '" + configurationName +
			                                        "' registered!");
		}

		return kiAimConfiguration.getExternalConfiguration().get(configurationName.toLowerCase());
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
}
