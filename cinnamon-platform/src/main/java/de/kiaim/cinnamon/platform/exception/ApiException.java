package de.kiaim.cinnamon.platform.exception;

import de.kiaim.cinnamon.platform.controller.ApiExceptionHandler;
import org.springframework.http.HttpStatusCode;

/**
 * Class for exceptions that can occur during api requests.
 */
public abstract class ApiException extends Exception {

	private static final String ERROR_CODE_PREFIX = "PLATFORM";
	private static final String ERROR_CODE_SEPARATOR = "_";

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
	 * See {@link ApiExceptionHandler} for more details.
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
	 * Code has the following structure PLATFORM_[ExceptionTypeCode]_[ExceptionClassCode]_[ExceptionCode].
	 * @return String containing the error code.
	 */
	public String getErrorCode() {
		return assembleErrorCode(getExceptionTypeCode(), getExceptionClassCode(), exceptionCode);
	}

	/**
	 * Assembles the error code for the given components.
	 * Code has the following structure PLATFORM_[ExceptionTypeCode]_[ExceptionClassCode]_[ExceptionCode].
	 * @param exceptionTypeCode First component.
	 * @param exceptionClassCode Second component.
	 * @param exceptionCode Third component.
	 * @return String containing the error code.
	 */
	public static String assembleErrorCode(final String exceptionTypeCode, final String exceptionClassCode,
	                                       final String exceptionCode) {
		return ERROR_CODE_PREFIX + ERROR_CODE_SEPARATOR + exceptionTypeCode + ERROR_CODE_SEPARATOR +
		       exceptionClassCode + ERROR_CODE_SEPARATOR + exceptionCode;
	}
}
