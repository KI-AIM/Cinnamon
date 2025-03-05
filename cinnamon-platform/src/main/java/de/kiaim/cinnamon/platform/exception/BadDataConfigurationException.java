package de.kiaim.cinnamon.platform.exception;

import de.kiaim.cinnamon.model.enumeration.DataType;

/**
 * Exceptions regarding invalid data configurations.
 */
public class BadDataConfigurationException extends BadRequestException {

	/**
	 * Exception code invalid number of attributes.
	 */
	public static final String INVALID_NUMBER_OF_ATTRIBUTES = "1";

	/**
	 * Exception code for data type {@link DataType#UNDEFINED} in requests.
	 */
	public static final String UNDEFINED_DATA_TYPE = "2";

	public BadDataConfigurationException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return DATA_CONFIGURATION;
	}
}
