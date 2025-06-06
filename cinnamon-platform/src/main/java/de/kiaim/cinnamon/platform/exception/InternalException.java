package de.kiaim.cinnamon.platform.exception;

import de.kiaim.cinnamon.model.dto.ErrorDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Exceptions that occur in the API because of internal errors the user cannot fix.
 */
public abstract class InternalException extends ApiException {

	/**
	 * Exception class code for exceptions from {@link InternalDataSetPersistenceException}.
	 */
	public static final String DATA_SET_PERSISTENCE = "1";

	/**
	 * Exception class code for exceptions from {@link InternalMissingHandlingException}.
	 */
	public static final String MISSING_HANDLING = "2";

	/**
	 * Exception class code for exceptions from {@link InternalIOException}.
	 */
	public static final String IO = "3";

	/**
	 * Exception class code for exceptions from {@link InternalRequestException}.
	 */
	public static final String HTTP = "4";

	/**
	 * Exception class code for exceptions from {@link InternalApplicationConfigurationException}.
	 */
	public static final String APPLICATION_CONFIGURATION = "5";

	/**
	 * Exception class code for exceptions from {@link InternalInvalidStateException}.
	 */
	public static final String INVALID_STATE = "6";

	/**
	 * Exception class code for exceptions from {@link InternalInvalidResultException}.
	 */
	public static final String INVALID_RESULT = "7";

	public InternalException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	public InternalException(final String exceptionCode, final String message, final ErrorDetails errorDetails) {
		super(exceptionCode, message, errorDetails);
	}


	public InternalException(final String exceptionCode, final String message, final Exception cause) {
		super(exceptionCode, message, cause);
	}

	@Override
	public HttpStatusCode getStatus() {
		return HttpStatus.INTERNAL_SERVER_ERROR;
	}

	@Override
	protected String getExceptionTypeCode() {
		return INTERNAL;
	}
}
