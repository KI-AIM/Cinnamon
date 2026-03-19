package de.kiaim.cinnamon.platform.controller;

import com.fasterxml.jackson.databind.JsonMappingException;
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
import org.springframework.data.util.Pair;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
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
			final var message = resolveFieldErrorMessage(fieldError);
			addFieldError(message, errors);
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

	/**
	 * Resolves the field path and error message for the given field error.
	 *
	 * @param fieldError The field error.
	 * @return Pair containing the field path and error message.
	 */
	private Pair<String, String> resolveFieldErrorMessage(final FieldError fieldError) {
		if (fieldError.isBindingFailure()) {
			final var unwrappedMessage = tryExtractOriginalBindingMessage(fieldError);
			return unwrappedMessage != null
			       ? unwrappedMessage
			       : Pair.of(fieldError.getField(), "Failed to convert value");
		}

		return Pair.of(fieldError.getField(), fieldError.getDefaultMessage());
	}

	/**
	 * Tries to extract the original binding message from the given field error.
	 * Returns null if no original binding message could be extracted.
	 *
	 * @param fieldError The field error.
	 * @return Pair containing the field path and error message.
	 */
	@Nullable
	private Pair<String, String> tryExtractOriginalBindingMessage(final FieldError fieldError) {
		final List<Class<? extends Throwable>> candidates = List.of(
				org.springframework.core.convert.ConversionFailedException.class,
				TypeMismatchException.class,
				IllegalArgumentException.class
		);

		for (final Class<? extends Throwable> candidate : candidates) {
			try {
				final Throwable exception = fieldError.unwrap(candidate);
				final var message = extractBestMessage(exception, fieldError.getField());
				if (message != null) {
					return message;
				}
			} catch (final IllegalArgumentException ignored) {
				// not available
			}
		}

		return null;
	}

	/**
	 * Extracts the error message and field path from the given throwable.
	 *
	 * @param throwable Cause of the validation error.
	 * @param fieldName Root of the field path.
	 * @return Pair containing the field path and error message.
	 */
	@Nullable
	private Pair<String, String> extractBestMessage(final Throwable throwable, final String fieldName) {
		if (throwable == null) {
			return null;
		}

		if (throwable.getCause() instanceof JsonMappingException jsonMappingException) {
			final var path = jsonMappingException.getPath();
			var field = fieldName + ".";
			for (final var segment : path) {
				if (segment.getFieldName() != null) {
					field += segment.getFieldName();
				} else {
					field += "[" + segment.getIndex() + "]";
				}
			}

			final var message = jsonMappingException.getMessage().split("\n at")[0];
			return Pair.of(field, message);
		}

		return null;
	}

	/**
	 * Adds the given field error to the given errors map.
	 * The first element of the pair is the field name, the second element is the error message.
	 *
	 * @param fieldError The field error to be added.
	 * @param errors Map containing the errors.
	 */
	private void addFieldError(final Pair<String, String> fieldError, final Map<String, Set<String>> errors) {
		if (errors.containsKey(fieldError.getFirst())) {
			errors.get(fieldError.getFirst()).add(fieldError.getSecond());
		} else {
			final Set<String> fieldErrors = new HashSet<>();
			fieldErrors.add(fieldError.getSecond());
			errors.put(fieldError.getFirst(), fieldErrors);
		}
	}

}
