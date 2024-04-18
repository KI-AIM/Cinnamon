package de.kiaim.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Date;

@Schema(description = "Response for an invalid request.")
@Getter
@AllArgsConstructor
public class ErrorResponse {

	@Schema(description = "Timestamp of the request arriving.", example = "2023-12-05T13:33:23.296+00:00")
	final Date timestamp = new Date();

	@Schema(description = "Http status code.", example = "400")
	final int status;

	@Schema(description = "JSON containing a detailed error description",
	        example = "{\"email\":\"Email is not available!\"}")
	final Object errors;
}
