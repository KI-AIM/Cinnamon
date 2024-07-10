package de.kiaim.platform.model.enumeration;

import lombok.Getter;

/**
 * Steps of a project where the backend is involved.
 */
@Getter
public enum Step {
	UPLOAD(false),
	DATA_CONFIG(false),
	VALIDATION(false),
	ANONYMIZATION_CONFIG(true),
	;

	/**
	 * If the step requires external processing.
	 */
	private final boolean hasExternalProcessing;

	Step(boolean hasExternalProcessing) {
		this.hasExternalProcessing = hasExternalProcessing;
	}
}
