package de.kiaim.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.Date;

@Schema(description = "Response for an invalid request.")
@Getter
@AllArgsConstructor
public class ErrorResponse {

	@Schema(description = "Timestamp of the request arriving.", example = "2023-12-05T13:33:23.296+00:00")
	final Date timestamp = new Date();

	@Schema(description = "Http status code.", example = "400")
	final int status;

	@Schema(description = "Path of the request.", example = "/api/data")
	final String path;

	@Schema(description = "Code specifying the exact error.", example = "PLATFORM_1_5_1")
	final String errorCode;

	@Schema(description = "Short description of the error", example = "Unsupported fiel type: .txt")
	final String errorMessage;

	@Schema(description = "JSON containing a detailed error description. Not always available.",
	        example = "{\"email\":\"Email is not available!\"}")
	@Nullable
	final Object errorDetails;
}
