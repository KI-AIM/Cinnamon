package de.kiaim.platform.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class InternalException extends ApiException {
	public InternalException(String message) {
		super(message);
	}

	public InternalException(final String message, final Exception cause) {
		super(message, cause);
	}

	@Override
	public HttpStatusCode getStatus() {
		return HttpStatus.INTERNAL_SERVER_ERROR;
	}
}
