package de.kiaim.platform.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Exceptions that occur in the API because of invalid requests, for example, invalid column names.
 */
public abstract class BadRequestException extends ApiException {

	/**
	 * Error class code for {@link BadColumnNameException}.
	 */
	public static final String COLUMN_NAME = "1";

	/**
	 * Error class code for {@link BadConfigurationNameException}.
	 */
	public static final String CONFIGURATION_NAME = "2";

	/**
	 * Error class code for {@link BadDataSetIdException}.
	 */
	public static final String DATA_SET_ID = "3";

	/**
	 * Error class code for {@link BadDataTypeException}.
	 */
	public static final String DATA_TYPE = "4";

	/**
	 * Error class code for {@link BadFileException}.
	 */
	public static final String FILE = "5";

	public BadRequestException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	public BadRequestException(final String exceptionCode, final String message, final Exception cause) {
		super(exceptionCode, message, cause);
	}

	@Override
	public HttpStatusCode getStatus() {
		return HttpStatus.BAD_REQUEST;
	}

	@Override
	protected String getExceptionTypeCode() {
		return BAD_REQUEST;
	}
}
