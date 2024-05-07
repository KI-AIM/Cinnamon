package de.kiaim.platform.controller;

import de.kiaim.platform.exception.ApiException;
import de.kiaim.platform.service.DatabaseService;
import de.kiaim.platform.service.ResponseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

	final ResponseService responseService;

	@Autowired
	public ApiExceptionHandler(ResponseService responseService) {
		this.responseService = responseService;
	}

	//==============================
	//-- Custom exception handler --
	//==============================

	@ExceptionHandler(value = {ApiException.class})
	public ResponseEntity<Object> handleApiException(final ApiException apiException, final WebRequest webRequest) {
		LOGGER.error("An error occurred during a request", apiException);
		return responseService.prepareErrorResponseEntity(apiException);
	}

	//===================================
	//-- Overwritten exception handler --
	//===================================

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
	                                                              HttpHeaders headers, HttpStatusCode status,
	                                                              WebRequest request) {
		Map<String, List<String>> errors = new HashMap<>();
		for (final FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			if (errors.containsKey(fieldError.getField())) {
				errors.get(fieldError.getField()).add(fieldError.getDefaultMessage());
			} else {
				List<String> fieldErrors = new ArrayList<>();
				fieldErrors.add(fieldError.getDefaultMessage());
				errors.put(fieldError.getField(), fieldErrors);
			}
		}

		return responseService.prepareErrorResponseEntity(headers, status, errors);
	}

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(
			MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status,
			WebRequest request) {
		return responseService.prepareErrorResponseEntity(headers, status,
		                                                  "Missing parameter: '" + ex.getParameterName() + "'");
	}

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestPart(
			MissingServletRequestPartException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return responseService.prepareErrorResponseEntity(headers, status,
		                                                  "Missing part: '" + ex.getRequestPartName() + "'");
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(
			TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return responseService.prepareErrorResponseEntity(headers, status,
		                                                  "Invalid parameter: '" + ex.getPropertyName() + "'");
	}
}
