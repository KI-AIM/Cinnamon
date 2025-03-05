package de.kiaim.cinnamon.platform.exception;

/**
 * Exceptions that are caused because of invalid process IDs in requests.
 */
public class BadProcessIdException extends BadRequestException {

	/**
	 * Exception code for process IDs that have not corresponding process.
	 */
	public static final String NO_PROCESS = "1";

	public BadProcessIdException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return PROCESS_ID;
	}
}
