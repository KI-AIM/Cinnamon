package de.kiaim.platform.exception;

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

	public InternalRequestException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return HTTP;
	}
}
