package de.kiaim.platform.service;

import de.kiaim.platform.exception.ApiException;
import de.kiaim.platform.model.dto.ErrorResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class ResponseService {
	public ResponseEntity<Object> prepareErrorResponseEntity(final ApiException apiException) {
		return new ResponseEntity<>(prepareErrorResponseBody(apiException.getStatus(), apiException.getMessage()),
		                            apiException.getStatus());
	}

	public ResponseEntity<Object> prepareErrorResponseEntity(final HttpStatusCode status, final Object errors) {
		return new ResponseEntity<>(prepareErrorResponseBody(status, errors), status);
	}

	public ResponseEntity<Object> prepareErrorResponseEntity(final HttpHeaders headers,
	                                                         final HttpStatusCode status,
	                                                         final Object errors) {
		return new ResponseEntity<>(prepareErrorResponseBody(status, errors), headers, status);
	}

	public ErrorResponse prepareErrorResponseBody(final HttpStatusCode status, final Object errors) {
		return new ErrorResponse(status.value(), errors);
	}

}
