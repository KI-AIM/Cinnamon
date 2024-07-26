package de.kiaim.platform.model.enumeration;

import lombok.Getter;

/**
 * Steps of a project where the backend is involved.
 */
@Getter
public enum Step {
	WELCOME(0, false),
	UPLOAD(1, false),
	DATA_CONFIG(2, false),
	VALIDATION(3, false),
	ANONYMIZATION_CONFIG(4, true),
	SYNTHETIZATION_CONFIG(5, true),
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
}
