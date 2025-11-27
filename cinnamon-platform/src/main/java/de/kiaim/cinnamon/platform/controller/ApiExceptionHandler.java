package de.kiaim.cinnamon.platform.controller;

import de.kiaim.cinnamon.model.dto.ErrorDetails;
import de.kiaim.cinnamon.platform.exception.ApiException;
import de.kiaim.cinnamon.platform.service.ResponseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.ConversionNotSupportedException;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	/**
	 * Error class for invalid request structures.
	 */
	public static final String REQUEST_STRUCTURE_ERROR = "1";

	/**
	 * Error class for validation errors.
	 */
	public static final String VALIDATION_ERROR = "2";

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
		return responseService.prepareErrorResponseEntity(apiException, webRequest);
	}

	//===================================
	//-- Overwritten exception handler --
	//===================================

	/**
	 * {@inheritDoc}
	 *
	 * In case the value causing the issue is a multipart file,
	 * the reason for the failed conversion is that a file was provided instead of a value.
	 */
	@Override
	protected ResponseEntity<Object> handleConversionNotSupported(final ConversionNotSupportedException ex,
	                                                              final HttpHeaders headers,
	                                                              final HttpStatusCode status,
	                                                              final WebRequest request) {
		final Object value = ex.getValue();

		if (value instanceof MultipartFile) {
			final String errorCode = ApiException.assembleErrorCode(ApiException.VALIDATION, REQUEST_STRUCTURE_ERROR,
			                                                        "4");
			return responseService.prepareErrorResponseEntity(headers, request, HttpStatus.BAD_REQUEST,
			                                                  errorCode, "Parameter '" + ex.getPropertyName() +
			                                                             "' must not be a file!", null);
		}

		return super.handleConversionNotSupported(ex, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
	                                                              HttpHeaders headers, HttpStatusCode status,
	                                                              WebRequest request) {
		Map<String, Set<String>> errors = new HashMap<>();
		// The order of field errors is not deterministic
		for (final FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			if (errors.containsKey(fieldError.getField())) {
				errors.get(fieldError.getField()).add(fieldError.getDefaultMessage());
			} else {
				Set<String> fieldErrors = new HashSet<>();
				String message = fieldError.getDefaultMessage();

				// TODO hacked in message for failed conversion
				if (message.startsWith("Failed to convert property value of type")) {
					message = "Failed to convert value";
				}

				fieldErrors.add(message);
				errors.put(fieldError.getField(), fieldErrors);
			}
		}

		final String errorCode = ApiException.assembleErrorCode(ApiException.VALIDATION, VALIDATION_ERROR, "1");
		return responseService.prepareErrorResponseEntity(headers, request, status, errorCode,
		                                                  "Request validation failed",
		                                                  new ErrorDetails().withValidationErrors(errors));
	}

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(
			MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status,
			WebRequest request) {
		final String errorCode = ApiException.assembleErrorCode(ApiException.VALIDATION, REQUEST_STRUCTURE_ERROR, "1");
		return responseService.prepareErrorResponseEntity(headers, request, status, errorCode,
		                                                  "Missing parameter: '" + ex.getParameterName() + "'", null);
	}

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestPart(
			MissingServletRequestPartException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		final String errorCode = ApiException.assembleErrorCode(ApiException.VALIDATION, REQUEST_STRUCTURE_ERROR, "2");
		return responseService.prepareErrorResponseEntity(headers, request, status, errorCode,
		                                                  "Missing part: '" + ex.getRequestPartName() + "'", null);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(
			TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		final String errorCode = ApiException.assembleErrorCode(ApiException.VALIDATION, REQUEST_STRUCTURE_ERROR, "3");
		return responseService.prepareErrorResponseEntity(headers, request, status, errorCode,
		                                                  "Invalid parameter: '" + ex.getPropertyName() + "'", null);
	}
}
