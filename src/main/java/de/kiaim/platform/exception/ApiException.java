package de.kiaim.platform.exception;

import org.springframework.http.HttpStatusCode;

public abstract class ApiException extends Exception {

	public ApiException(final String message) {
		super(message);
	}

	public ApiException(final String message, final Exception cause) {
		super(message, cause);
	}

	public abstract HttpStatusCode getStatus();
}
