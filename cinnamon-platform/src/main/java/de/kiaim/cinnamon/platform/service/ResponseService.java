package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.platform.exception.ApiException;
import de.kiaim.cinnamon.platform.model.dto.ErrorResponse;
import org.springframework.http.HttpHeaders;
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
				                         apiException.getMessage(), null), apiException.getStatus());
	}

	public ResponseEntity<Object> prepareErrorResponseEntity(final HttpHeaders headers,
															 final WebRequest webRequest,
	                                                         final HttpStatusCode status,
															 final String errorCode,
															 final String message,
	                                                         @Nullable final Object details) {
		final String path = ((ServletWebRequest) webRequest).getRequest().getRequestURI();
		return new ResponseEntity<>(prepareErrorResponseBody(status, path, errorCode, message, details), headers,
		                            status);
	}

	public ErrorResponse prepareErrorResponseBody(final HttpStatusCode status, final String path,
	                                              final String errorCode, final String message,
	                                              @Nullable final Object details) {
		return new ErrorResponse(status.value(), path, errorCode, message, details);
	}

}
