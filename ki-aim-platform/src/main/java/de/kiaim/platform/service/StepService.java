package de.kiaim.platform.service;

import de.kiaim.platform.config.KiAimConfiguration;
import de.kiaim.platform.config.StepConfiguration;
import de.kiaim.platform.exception.BadStepNameException;
import de.kiaim.platform.exception.InternalApplicationConfigurationException;
import de.kiaim.platform.model.enumeration.Step;
import org.springframework.stereotype.Service;

@Service
public class StepService {

	private final KiAimConfiguration kiAimConfiguration;

	public StepService(KiAimConfiguration kiAimConfiguration) {
		this.kiAimConfiguration = kiAimConfiguration;
	}

	public StepConfiguration getStepConfiguration(final Step step) throws InternalApplicationConfigurationException {
		try {
			return this.getStepConfiguration(step.name());
		} catch (BadStepNameException e) {
			throw new InternalApplicationConfigurationException(
					InternalApplicationConfigurationException.MISSING_STEP_CONFIGURATION,
					"No configuraiton for the step '" + step.name() + "' found!", e);
		}
	}

	public StepConfiguration getStepConfiguration(final String stepName) throws BadStepNameException {
		if (!kiAimConfiguration.getSteps().containsKey(stepName)) {
			throw new BadStepNameException(BadStepNameException.NOT_FOUND,
			                               "The step '" + stepName + "' is not defined!");
		}
		return kiAimConfiguration.getSteps().get(stepName);
	}
}
