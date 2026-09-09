package de.kiaim.cinnamon.platform.model.enumeration;

import de.kiaim.cinnamon.platform.exception.BadStepNameException;
import lombok.Getter;
import org.springframework.lang.Nullable;

/**
 * Steps of a project where the backend is involved.
 */
@Getter
public enum Step {
	WELCOME(0),
	UPLOAD(1),
	DATA_CONFIG(2),
	DATA_EXTRACTION(3),
	VALIDATION(4),
	ANONYMIZATION(5),
	SYNTHETIZATION(6),
	EXECUTION(7),
	TECHNICAL_EVALUATION(8),
	RISK_EVALUATION(9),
	EVALUATION(10),
	REPORT(11),
	;

	/**
	 * Index of the step.
	 */
	private final int index;

	Step(final int index) {
		this.index = index;
	}

	/**
	 * Returns the step with the given name or null.
	 * @param stepName The name of the step.
	 * @return The step or null.
	 */
	@Nullable
	public static Step getStep(final String stepName) {
		for (final Step step : values()) {
			if (step.name().equalsIgnoreCase(stepName)) {
				return step;
			}
		}

		return null;
	}

	/**
	 * Returns the step with the given name.
	 * @param stepName The step name.
	 * @return The step.
	 * @throws BadStepNameException If no step with the given name exists.
	 */
	public static Step getStepOrThrow(final String stepName) throws BadStepNameException {
		final Step step = Step.getStep(stepName);
		if (step == null) {
			throw new BadStepNameException(BadStepNameException.NOT_FOUND,
			                               "The step '" + stepName + "' is not defined!");
		}
		return step;
	}
}
