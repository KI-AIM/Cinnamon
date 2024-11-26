package de.kiaim.platform.model.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class LoadDataRequest {

	@Parameter(description = "Names of the columns that should be returned, separated by commas. If empty, all columns will be returned.",
	           required = false,
	           content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
	                              schema = @Schema(implementation = String.class),
	                              examples = {
			                              @ExampleObject("name,age,birthdate")
	                              }))
	private String columns = "";

	@Parameter(description = "Default encoding for all transformation errors. Special values are '$null' for JSON null and '$value' for the original value.",

	           required = false,
	           content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
	                              schema = @Schema(implementation = String.class),
	                              examples = {
			                              @ExampleObject("$null"),
			                              @ExampleObject("N/A")
	                              }))
	private String defaultNullEncoding = "$null";

	@Parameter(description = "Encoding for all values that are missing in the original data. Special values are '$null' for JSON null and '$value' for the original value.",
			   required = false,
	           content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
	                              schema = @Schema(implementation = String.class),
	                              examples = {
			                              @ExampleObject("$null"),
			                              @ExampleObject("N/A")
	                              }))
	@Nullable
	private String missingValueEncoding = null;

	@Parameter(description = "Encoding for all values that could not be transformed into the specified data type. Special values are '$null' for JSON null and '$value' for the original value.",
	           required = false,
	           content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
	                              schema = @Schema(implementation = String.class),
	                              examples = {
			                              @ExampleObject("$null"),
			                              @ExampleObject("N/A")
	                              }))
	@Nullable
	private String formatErrorEncoding = null;

	@Parameter(description = "Encoding for all values that are not in the specified range. Special values are '$null' for JSON null and '$value' for the original value.",
	           required = false,
	           content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
	                              schema = @Schema(implementation = String.class),
	                              examples = {
			                              @ExampleObject("$null"),
			                              @ExampleObject("N/A")
	                              }))
	@Nullable
	private String valueNotInRangeEncoding = null;

	public List<String> getColumnNames() {
		return !columns.isBlank()
		       ? List.of(columns.split(","))
		       : new ArrayList<>();
	}
}
