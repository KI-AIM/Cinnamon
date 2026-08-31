package de.kiaim.cinnamon.platform.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Exception for issues regarding the mail settings of the application.
 *
 * @author Daniel Preciado-Marquez
 */
public class BadMailSettingsException extends BadRequestException {

	/**
	 * Exception code for mail settings that have not been configured yet.
	 */
	public static final String NOT_FOUND = "1";

	public BadMailSettingsException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	public HttpStatusCode getStatus() {
		return HttpStatus.NOT_FOUND;
	}

	@Override
	protected String getExceptionClassCode() {
		return MAIL_SETTINGS;
	}
}
