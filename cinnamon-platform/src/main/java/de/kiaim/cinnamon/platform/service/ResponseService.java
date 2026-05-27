package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.model.dto.ErrorDetails;
import de.kiaim.cinnamon.platform.exception.ApiException;
import de.kiaim.cinnamon.model.dto.ErrorResponse;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

@Service
public class ResponseService {
	public ResponseEntity<Object> prepareErrorResponseEntity(final ApiException apiException,
	                                                         final WebRequest webRequest) {
		final String path = ((ServletWebRequest) webRequest).getRequest().getRequestURI();
		final ErrorResponse body = prepareErrorResponseBody(apiException.getStatus(), path, apiException.getErrorCode(),
		                                                    apiException.getMessage(), apiException.getErrorDetails());
		return buildResponseEntity(body);
	}

	public ResponseEntity<Object> prepareErrorResponseEntity(final HttpHeaders headers,
	                                                         final WebRequest webRequest,
	                                                         final HttpStatusCode status,
	                                                         final String errorCode,
	                                                         final String message,
	                                                         @Nullable final ErrorDetails details) {
		final String path = ((ServletWebRequest) webRequest).getRequest().getRequestURI();
		final ErrorResponse body = prepareErrorResponseBody(status, path, errorCode, message, details);
		return buildResponseEntity(body, headers);
	}

	public ErrorResponse prepareErrorResponseBody(final HttpStatusCode status, final String path,
	                                              final String errorCode, final String message,
	                                              @Nullable final ErrorDetails errorDetails) {
		return new ErrorResponse("about:blank", HttpStatus.valueOf(status.value()).name(), status.value(), path,
		                         errorCode, message, errorDetails);
	}

	/**
	 * Creates a response entity for an error with default http headers.
	 *
	 * @param problemDetail The body of the response.
	 * @return The response entity.
	 */
	private ResponseEntity<Object> buildResponseEntity(final ErrorResponse problemDetail) {
		return buildResponseEntity(problemDetail, new HttpHeaders());
	}

	/**
	 * Creates a response entity for an error.
	 *
	 * @param problemDetail The body of the response.
	 * @param headers       The headers of the response.
	 * @return The response entity.
	 */
	private ResponseEntity<Object> buildResponseEntity(final ErrorResponse problemDetail, final HttpHeaders headers) {
		return ResponseEntity.status(problemDetail.getStatus())
		                     .contentType(MediaType.APPLICATION_PROBLEM_JSON)
		                     .headers(headers)
		                     .body(problemDetail);
	}

}
