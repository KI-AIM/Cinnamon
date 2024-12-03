package de.kiaim.platform.exception;

/**
 * Exceptions regarding invalid data configurations.
 */
public class BadDataConfigurationException extends BadRequestException {

	/**
	 * Exception code invalid number of attributes.
	 */
	public static final String INVALID_NUMBER_OF_ATTRIBUTES = "1";

	public BadDataConfigurationException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return DATA_CONFIGURATION;
	}
}
