package de.kiaim.platform.model.enumeration;

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

	@Nullable
	public static Step getStep(final String stepName) {
		for (final Step step : values()) {
			if (step.name().equalsIgnoreCase(stepName)) {
				return step;
			}
		}

		return null;
	}
}
