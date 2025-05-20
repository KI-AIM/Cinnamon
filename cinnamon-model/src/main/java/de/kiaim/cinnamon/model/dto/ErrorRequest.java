package de.kiaim.cinnamon.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.lang.Nullable;

import java.util.Date;

/**
 * Request for error occurring when running a process.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Request for error occurring when running a process.")
@Getter
@AllArgsConstructor
public class ErrorRequest {
	/**
	 * URL describing the error.
	 */
	@Schema(description = "URL linking to a description of the error.", example = "about:blank",
	        requiredMode = Schema.RequiredMode.REQUIRED)
	private String type = "about:blank";

	/**
	 * Timestamp of the error.
	 */
	@Schema(description = "Timestamp of the request arriving.", example = "2023-12-05T13:33:23.296+00:00",
	        requiredMode = Schema.RequiredMode.REQUIRED)
	private final Date timestamp = new Date();

	/**
	 * Error Code in the form [SOURCE]_[ExceptionTypeCode]_[ExceptionClassCode]_[ExceptionCode].
	 */
	@Schema(description = "Code specifying the exact error.", example = "PLATFORM_1_5_1",
	        requiredMode = Schema.RequiredMode.REQUIRED)
	private final String errorCode;

	/**
	 * Human-readable message of the error.
	 */
	@Schema(description = "Short description of the error", example = "Unsupported fiel type: .txt",
	        requiredMode = Schema.RequiredMode.REQUIRED)
	private final String errorMessage;

	/**
	 * Additional information for the error as JSON. Content depends on the specific error.
	 */
	@Schema(description = "JSON containing a detailed error description. Not always available.",
	        example = "{\"email\":\"Email is not available!\"}", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	@Nullable
	private final Object errorDetails;
}
