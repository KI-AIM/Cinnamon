package de.kiaim.platform.model.enumeration;

import de.kiaim.platform.exception.BadStepNameException;
import lombok.Getter;
import org.springframework.lang.Nullable;

/**
 * Steps of a project where the backend is involved.
 */
@Getter
public enum Step {
	WELCOME(0, false),
	UPLOAD(1, false),
	DATA_CONFIG(2, false),
	VALIDATION(3, false),
	ANONYMIZATION(4, true),
	SYNTHETIZATION(5, true),
	;

	/**
	 * Index of the step.
	 */
	private final int index;

	/**
	 * If the step requires external processing.
	 */
	private final boolean hasExternalProcessing;


	Step(final int index, final boolean hasExternalProcessing) {
		this.index = index;
		this.hasExternalProcessing = hasExternalProcessing;
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
