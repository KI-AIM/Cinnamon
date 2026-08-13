package de.kiaim.cinnamon.platform.exception;

/**
 * Exception wrapper for errors occurring while sending emails.
 */
public class InternalMailException extends InternalException {

	/**
	 * Exception code for a failed email sending attempt.
	 */
	public static final String SENDING = "1";

	/**
	 * Exception code for a missing email body.
	 * This can only occur if the validation in a previous step is not working properly.
	 */
	public static final String MISSING_BODY = "2";

	/**
	 * Exception code for a failed replacement of placeholder in the mail body.
	 */
	public static final String BODY_PLACEHOLDER_REPLACEMENT = "3";

	public InternalMailException(final String missingBody, final String message) {
		super(missingBody, message);
	}

	public InternalMailException(final String exceptionCode, final String message, final Exception cause) {
		super(exceptionCode, message, cause);
	}

	@Override
	protected String getExceptionClassCode() {
		return MAIL;
	}
}
