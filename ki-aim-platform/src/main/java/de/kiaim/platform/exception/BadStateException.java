package de.kiaim.platform.exception;

public class BadStateException extends BadRequestException {

	/**
	 * Exception code for modifying a configuration when the process is already scheduled or started.
	 */
	public static final String PROCESS_STARTED = "1";

	public BadStateException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return STATE;
	}
}
