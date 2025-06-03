package de.kiaim.cinnamon.platform.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Exception for failed user confirmation.
 *
 * @author Daniel Preciado-Marquez
 */
public class BadUserConfirmationException extends BadRequestException {

	public static final String INVALID_EMAIL = "1";
	public static final String INVALID_PASSWORD = "2";

	public BadUserConfirmationException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return USER_CONFIRMATION;
	}

	@Override
	public HttpStatusCode getStatus() {
		return HttpStatus.FORBIDDEN;
	}
}
