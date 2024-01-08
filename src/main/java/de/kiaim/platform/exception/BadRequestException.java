package de.kiaim.platform.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class BadRequestException extends ApiException {
	public BadRequestException(String message) {
		super(message);
	}

	@Override
	public HttpStatusCode getStatus() {
		return HttpStatus.BAD_REQUEST;
	}
}
