package de.kiaim.cinnamon.platform.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Exception for a bad workflow, e.g., a workflow that could not be found.
 *
 * @author Daniel Preciado-Marquez
 */
public class BadProjectException extends BadRequestException {

	/**
	 * Exception code for a workflow that could not be found.
	 */
	public static final String NOT_FOUND = "1";

	public BadProjectException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	public HttpStatusCode getStatus() {
		return HttpStatus.NOT_FOUND;
	}

	@Override
	protected String getExceptionClassCode() {
		return WORKFLOW;
	}
}
