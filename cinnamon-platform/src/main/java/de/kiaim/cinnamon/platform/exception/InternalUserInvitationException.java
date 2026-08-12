package de.kiaim.cinnamon.platform.exception;

/**
 * Exception thrown when an error occurs during user invitation operations.
 *
 * @author Daniel Preciado-Marquez
 */
public class InternalUserInvitationException extends InternalException {

	public static final String TOKEN_GENERATION_FAILED = "1";

	public InternalUserInvitationException(final String exceptionCode, final String message, final Throwable cause) {
		super(exceptionCode, message, cause);
	}

	@Override
	protected String getExceptionClassCode() {
		return USER_INVITATION;
	}
}
