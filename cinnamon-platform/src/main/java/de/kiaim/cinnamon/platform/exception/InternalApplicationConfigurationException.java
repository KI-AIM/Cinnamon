package de.kiaim.cinnamon.platform.exception;

/**
 * Exceptions that occur in the API because of an invalid application configuration.
 */
public class InternalApplicationConfigurationException extends InternalException {

	/**
	 * Exception code for missing step configuration.
	 */
	public static final String MISSING_STEP_CONFIGURATION = "1";

	/**
	 * Exception code for an invalid input dataset in the configuration.
	 */
	public static final String INVALID_INPUT_DATA_SET = "2";

	/**
	 * Exception code for missing step configuration.
	 */
	public static final String MISSING_STAGE_CONFIGURATION = "3";

	/**
	 * Exception code for multiple usages of the same job.
	 */
	public static final String MULTIPLE_JOB_USAGE = "4";

	public InternalApplicationConfigurationException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	public InternalApplicationConfigurationException(final String exceptionCode, final String message,
	                                                 final Exception cause) {
		super(exceptionCode, message, cause);
	}

	@Override
	protected String getExceptionClassCode() {
		return APPLICATION_CONFIGURATION;
	}
}
