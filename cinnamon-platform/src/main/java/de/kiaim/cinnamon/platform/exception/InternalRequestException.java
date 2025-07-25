package de.kiaim.cinnamon.platform.exception;

import de.kiaim.cinnamon.model.dto.ErrorDetails;

/**
 * Exception for failed HTTP requests with other modules.
 */
public class InternalRequestException extends InternalException {

	/**
	 * Exception code for failed process start.
	 */
	public static final String PROCESS_START = "1";

	/**
	 * Exception code for failed process cancellation.
	 */
	public static final String PROCESS_CANCEL = "2";

	/**
	 * Exception code for failed process status retrieval.
	 */
	public static final String PROCESS_STATUS = "3";

	/**
	 * Exception code for failing algorithms fetching.
	 */
	public static final String ALGORITHMS = "4";

	/**
	 * Exception code for failed fetching of configuration definition.
	 */
	public static final String CONFIGURATION_DEFINITION = "5";

	/**
	 * Exception code for unavailable external server instances.
	 */
	public static final String NO_INSTANCE_AVAILABLE = "6";

	public InternalRequestException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	public InternalRequestException(final String exceptionCode, final String message, final ErrorDetails errorDetails) {
		super(exceptionCode, message, errorDetails);
	}

	@Override
	protected String getExceptionClassCode() {
		return HTTP;
	}
}
