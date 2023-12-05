package de.kiaim.platform.controller;

import de.kiaim.platform.model.dto.ErrorResponse;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
	                                                              HttpHeaders headers, HttpStatusCode status,
	                                                              WebRequest request) {
		Map<String, String> errors = ex.getBindingResult()
		                               .getFieldErrors()
		                               .stream()
		                               .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage));
		return prepareResponseEntity(headers, status, errors);
	}

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(
			MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status,
			WebRequest request) {
		return prepareResponseEntity(headers, status, "Missing parameter: '" + ex.getParameterName() + "'");
	}

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestPart(
			MissingServletRequestPartException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return prepareResponseEntity(headers, status, "Missing part: '" + ex.getRequestPartName() + "'");
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(
			TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return prepareResponseEntity(headers, status, "Invalid parameter: '" + ex.getPropertyName() + "'");
	}

	private ResponseEntity<Object> prepareResponseEntity(final HttpHeaders headers,
	                                                     final HttpStatusCode status,
	                                                     final Object errors) {
		return new ResponseEntity<>(prepareResponseBody(status, errors), headers, status);
	}

	private ErrorResponse prepareResponseBody(final HttpStatusCode status, final Object errors) {
		return new ErrorResponse(status.value(), errors);
	}
}
