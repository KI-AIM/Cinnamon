package de.kiaim.platform.model.enumeration;

import de.kiaim.platform.exception.BadStepNameException;
import lombok.Getter;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Steps of a project where the backend is involved.
 */
@Getter
public enum Step {
	WELCOME(0, List.of()),
	UPLOAD(1, List.of()),
	DATA_CONFIG(2, List.of()),
	VALIDATION(3, List.of()),
	ANONYMIZATION(4, List.of()),
	SYNTHETIZATION(5, List.of()),
	EXECUTION(6, List.of(ANONYMIZATION, SYNTHETIZATION)),
	;

	/**
	 * Index of the step.
	 */
	private final int index;

	/**
	 * List of processes in this step.
	 */
	private final List<Step> processes;


	Step(final int index, final List<Step> processes) {
		this.index = index;
		this.processes = processes;
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
