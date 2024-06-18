package de.kiaim.platform.exception;

/**
 * Exceptions that are cause because of invalid data types in requests.
 */
public class BadDataTypeException extends BadRequestException {

	/**
	 * Exception code for data type {@link de.kiaim.model.enumeration.DataType#UNDEFINED} in requests.
	 */
	public static final String UNDEFINED_NOT_ALLOWED = "1";

	public BadDataTypeException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return DATA_TYPE;
	}
}
