package de.kiaim.cinnamon.platform.exception;

/**
 * Exception thrown when a user invitation is invalid or cannot be processed.
 */
public class BadUserInvitationException extends BadRequestException {

	public static final String ID_NOT_FOUND = "1";

	public static final String ALREADY_ACCEPTED = "2";

	public static final String TOKEN_NOT_FOUND = "3";

	public static final String EXPIRED = "4";

	public static final String REVOKED = "5";

	public BadUserInvitationException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	public BadUserInvitationException(final String exceptionCode, final String message, final Throwable cause) {
		super(exceptionCode, message, cause);
	}

	@Override
	protected String getExceptionClassCode() {
		return USER_INVITATION;
	}
}
