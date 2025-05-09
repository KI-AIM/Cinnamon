package de.kiaim.cinnamon.platform.exception;

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

	public InternalRequestException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return HTTP;
	}
}
