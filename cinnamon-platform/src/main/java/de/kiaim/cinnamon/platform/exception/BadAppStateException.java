package de.kiaim.cinnamon.platform.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Exceptions occurring when the state of the app does not allow a certain action.
 *
 * @author Daniel Preciado-Marquez
 */
public class BadAppStateException extends BadRequestException {

	/**
	 * Exception code for attempting to remove the last admin user.
	 */
	public static final String REMOVING_LAST_ADMIN = "1";

	/**
	 * Exception code for attempting to register a user when registration requires an invitation.
	 */
	public static final String REGISTRATION_REQUIRES_INVITATION = "2";

	public BadAppStateException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	public HttpStatusCode getStatus() {
		return HttpStatus.CONFLICT;
	}

	@Override
	protected String getExceptionClassCode() {
		return APP_STATE;
	}
}
