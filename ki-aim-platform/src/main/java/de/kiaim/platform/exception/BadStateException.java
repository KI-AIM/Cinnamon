package de.kiaim.platform.exception;

/**
 * Exceptions for when the state of the application does not allow a certain action.
 */
public class BadStateException extends BadRequestException {

	/**
	 * Exception code for modifying a configuration when the process is already scheduled or started.
	 */
	public static final String PROCESS_STARTED = "1";

	/**
	 * Exception code for actions that require the file for the data set to be selected.
	 */
	public static final String NO_DATASET_FILE = "2";

	public BadStateException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return STATE;
	}
}
