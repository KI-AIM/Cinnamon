package de.kiaim.platform.exception;

/**
 * Exception for invalid application states.
 */
public class InternalInvalidStateException extends InternalException {

	/**
	 * Exception code for missing {@link de.kiaim.platform.model.entity.ExternalProcessEntity} for a step that requires external processing.
	 */
	public static final String MISSING_PROCESS_ENTITY = "1";

	/**
	 * Exception code for missing configurations that are required to start a process.
	 */
	public static final String MISSING_CONFIGURATION = "2";

	/**
	 * Exception code for trying to start the next process if the previous process is not finished or skipped.
	 */
	public static final String LAST_STEP_NOT_FINISHED = "3";

	/**
	 * Exception code for a missing data set that should be present based on the status of the project.
	 */
	public static final String MISSING_DATA_STET = "4";

	public InternalInvalidStateException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return INVALID_STATE;
	}
}
