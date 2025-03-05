package de.kiaim.platform.exception;

/**
 * Exceptions that are cause because of invalid column names in requests.
 */
public class BadColumnNameException extends BadRequestException {

	/**
	 * Exception code for column names that could not be found.
	 */
	public static final String NOT_FOUND = "1";

	public BadColumnNameException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return COLUMN_NAME;
	}
}
