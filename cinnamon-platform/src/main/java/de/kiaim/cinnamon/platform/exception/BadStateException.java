package de.kiaim.cinnamon.platform.exception;

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

	/**
	 * Exception code for actions that require a data set from a specific step to be present.
	 */
	public static final String NO_DATA_SET = "3";

	/**
	 * Exception code for actions that may not be done after the data has been confirmed.
	 */
	public static final String DATE_CONFIRMED = "4";

	/**
	 * Exception code for actions that require a configuration to be set.
	 */
	public static final String CONFIGURATION = "5";

	public static final String PRECEDING_JOB_NOT_FINISHED = "6";

	public BadStateException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return STATE;
	}
}
