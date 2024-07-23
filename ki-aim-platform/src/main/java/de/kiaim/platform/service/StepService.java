package de.kiaim.platform.service;

import de.kiaim.platform.config.KiAimConfiguration;
import de.kiaim.platform.config.StepConfiguration;
import de.kiaim.platform.exception.BadStepNameException;
import org.springframework.stereotype.Service;

@Service
public class StepService {

	private final KiAimConfiguration kiAimConfiguration;

	public StepService(KiAimConfiguration kiAimConfiguration) {
		this.kiAimConfiguration = kiAimConfiguration;
	}

	public StepConfiguration getStepConfiguration(final String stepName) throws BadStepNameException {
		if (!kiAimConfiguration.getSteps().containsKey(stepName)) {
			throw new BadStepNameException(BadStepNameException.NOT_FOUND,
			                               "The step '" + stepName + "' is not defined!");
		}
		return kiAimConfiguration.getSteps().get(stepName);
	}
}
