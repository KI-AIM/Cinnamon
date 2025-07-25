package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.model.dto.ErrorDetails;
import de.kiaim.cinnamon.platform.exception.ApiException;
import de.kiaim.cinnamon.model.dto.ErrorResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

@Service
public class ResponseService {
	public ResponseEntity<Object> prepareErrorResponseEntity(final ApiException apiException,
	                                                         final WebRequest webRequest) {
		final String path = ((ServletWebRequest) webRequest).getRequest().getRequestURI();
		return new ResponseEntity<>(
				prepareErrorResponseBody(apiException.getStatus(), path, apiException.getErrorCode(),
				                         apiException.getMessage(), apiException.getErrorDetails()),
				apiException.getStatus());
	}

	public ResponseEntity<Object> prepareErrorResponseEntity(final HttpHeaders headers,
	                                                         final WebRequest webRequest,
	                                                         final HttpStatusCode status,
	                                                         final String errorCode,
	                                                         final String message,
	                                                         @Nullable final ErrorDetails details) {
		final String path = ((ServletWebRequest) webRequest).getRequest().getRequestURI();
		return new ResponseEntity<>(prepareErrorResponseBody(status, path, errorCode, message, details), headers,
		                            status);
	}

	public ErrorResponse prepareErrorResponseBody(final HttpStatusCode status, final String path,
	                                              final String errorCode, final String message,
	                                              @Nullable final ErrorDetails errorDetails) {
		return new ErrorResponse("about:blank", HttpStatus.valueOf(status.value()).name(), status.value(), path,
		                         errorCode, message, errorDetails);
	}

}
