package de.kiaim.cinnamon.platform.exception;

/**
 * Exception thrown when an internal error occurs within the Cinnamon platform.
 * Collection of internal exceptions that do not fit into the existing exception hierarchy.
 */
public class InternalErrorException extends InternalException {

	/**
	 * Exception code for reaching the maximum number of retries when generating a workflow ID.
	 */
	public static final String GEN_EXTERNAL_ID_MAX_RETRIES = "1";

	public InternalErrorException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return OTHER;
	}
}
