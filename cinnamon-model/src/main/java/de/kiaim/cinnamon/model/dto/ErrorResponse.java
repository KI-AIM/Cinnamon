package de.kiaim.cinnamon.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.lang.Nullable;

import java.util.Date;

/**
 * Response for an invalid request.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Response for an invalid request.")
@Getter
@AllArgsConstructor
public class ErrorResponse {

	/**
	 * URL describing the error.
	 */
	@Schema(description = "URL linking to a description of the error.", example = "about:blank",
	        requiredMode = Schema.RequiredMode.REQUIRED)
	private String type = "about:blank";

	/**
	 * Name of the status code.
	 */
	@Schema(description = "Name of the HTTP status code.", example = "BAD_REQUEST",
	        requiredMode = Schema.RequiredMode.REQUIRED)
	private String title;

	/**
	 * Timestamp of the error.
	 */
	@Schema(description = "Timestamp of the request arriving.", example = "2023-12-05T13:33:23.296+00:00",
	        requiredMode = Schema.RequiredMode.REQUIRED)
	final Date timestamp = new Date();

	/**
	 * Value of the status code.
	 */
	@Schema(description = "Http status code.", example = "400", requiredMode = Schema.RequiredMode.REQUIRED)
	final int status;

	/**
	 * URL of the called API.
	 */
	@Schema(description = "Path of the request.", example = "/api/data", requiredMode = Schema.RequiredMode.REQUIRED)
	final String path;

	/**
	 * Error Code in the form [SOURCE]_[ExceptionTypeCode]_[ExceptionClassCode]_[ExceptionCode].
	 */
	@Schema(description = "Code specifying the exact error.", example = "PLATFORM_1_5_1",
	        requiredMode = Schema.RequiredMode.REQUIRED)
	final String errorCode;

	/**
	 * Human-readable message of the error.
	 */
	@Schema(description = "Short description of the error", example = "Unsupported fiel type: .txt",
	        requiredMode = Schema.RequiredMode.REQUIRED)
	final String errorMessage;

	/**
	 * Additional information for the error as JSON. Content depends on the specific error.
	 */
	@Schema(description = "JSON containing a detailed error description. Not always available.",
	        example = "{\"email\":\"Email is not available!\"}", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	@Nullable
	final ErrorDetails errorDetails;
}
