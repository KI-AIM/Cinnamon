package de.kiaim.platform.exception;

/**
 * Exceptions that occur when a switch case has no implementation.
 */
public class InternalMissingHandlingException extends InternalException {

	 /**
	 * Exception code for missing request type handling.
	 */
	public static final String REQUEST_TYPE = "1";

	/**
	 * Exception code for missing file type handling.
	 */
	public static final String FILE_TYPE = "2";

	/**
	 * Exception code for missing data set selector handling.
	 */
	public static final String DATA_SET_SELECTOR = "3";

	/**
	 * Exception code for missing step input encoding.
	 */
	public static final String STEP_INPUT_ENCODING = "4";

	public InternalMissingHandlingException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return MISSING_HANDLING;
	}
}
