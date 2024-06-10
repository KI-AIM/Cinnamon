package de.kiaim.platform.exception;

import org.springframework.http.HttpStatusCode;

/**
 * Class for exceptions that can occur during api requests.
 */
public abstract class ApiException extends Exception {

	/**
	 * Exception type code for exceptions that extend {@link BadRequestException}.
	 */
	public static final String BAD_REQUEST = "1";

	/**
	 * Exception type code for exceptions that extend {@link InternalException}.
	 */
	public static final String INTERNAL = "2";

	/**
	 * Exception type code for failed validation and invalid request structure.
	 * See {@link de.kiaim.platform.controller.ApiExceptionHandler} for more details.
	 */
	public static final String VALIDATION = "3";

	private final String exceptionCode;

	public ApiException(final String exceptionCode, final String message) {
		super(message);
		this.exceptionCode = exceptionCode;
	}

	public ApiException(final String exceptionCode, final String message, final Exception cause) {
		super(message, cause);
		this.exceptionCode = exceptionCode;
	}

	public abstract HttpStatusCode getStatus();

	protected abstract String getExceptionTypeCode();
	protected abstract String getExceptionClassCode();

	/**
	 * Returns the error code of the exception.
	 * Code has the following structure [ExceptionTypeCode]-[ExceptionClassCode]-[ExceptionCode].
	 * @return String containing the error code.
	 */
	public String getErrorCode() {
		return getExceptionTypeCode() + "-" +  getExceptionClassCode() + exceptionCode;
	}
}
