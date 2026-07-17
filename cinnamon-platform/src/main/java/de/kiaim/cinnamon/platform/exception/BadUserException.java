package de.kiaim.cinnamon.platform.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Exception for a bad user, e.g., a user that could not be found.
 *
 * @author Daniel Preciado-Marquez
 */
public class BadUserException extends BadRequestException {

	/**
	 * Exception code for a user that could not be found.
	 */
	public static final String NOT_FOUND = "1";

	/**
	 * Exception code for a username that is already in use.
	 */
	public static final String ALREADY_EXISTS = "2";

	public BadUserException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	public HttpStatusCode getStatus() {
		return HttpStatus.NOT_FOUND;
	}

	@Override
	protected String getExceptionClassCode() {
		return USER;
	}
}
