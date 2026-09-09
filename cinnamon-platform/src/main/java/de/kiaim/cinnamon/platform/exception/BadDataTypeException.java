package de.kiaim.cinnamon.platform.exception;

import de.kiaim.cinnamon.model.enumeration.DataType;

/**
 * Exceptions that are cause because of invalid data types in requests.
 */
public class BadDataTypeException extends BadRequestException {

	/**
	 * Exception code for data type {@link DataType#UNDEFINED} in requests.
	 */
	public static final String UNDEFINED_NOT_ALLOWED = "1";

	/**
	 * Exception code for a text extraction source column that is not free text.
	 */
	public static final String TEXT_REQUIRED = "2";

	public BadDataTypeException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return DATA_TYPE;
	}
}
