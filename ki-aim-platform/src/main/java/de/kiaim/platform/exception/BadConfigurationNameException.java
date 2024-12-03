package de.kiaim.platform.exception;

/**
 * Exceptions that are cause because of invalid configuration names in requests.
 */
public class BadConfigurationNameException extends BadRequestException {

	/**
	 * Exception code for configuration names that could not be found.
	 */
	public static final String NOT_FOUND = "1";

	/**
	 * Exception code for column names that could not be found.
	 */
	public BadConfigurationNameException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return CONFIGURATION_NAME;
	}
}
