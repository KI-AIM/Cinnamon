package de.kiaim.cinnamon.platform.exception;

/**
 * Exception wrapper for errors occurring while sending emails.
 */
public class InternalMailException extends InternalException {

	/**
	 * Exception code for a failed email sending attempt.
	 */
	public static final String SENDING = "1";

	public InternalMailException(final String exceptionCode, final String message, final Exception cause) {
		super(exceptionCode, message, cause);
	}

	@Override
	protected String getExceptionClassCode() {
		return MAIL;
	}
}
