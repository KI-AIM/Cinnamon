package de.kiaim.platform.model.file;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor @NoArgsConstructor
@Getter @Setter
@Schema(description = "Configurations specific for CSV files.")
public class CsvFileConfiguration {

	@Schema(description = "Character separating the columns.", example = ",")
	@NotNull(message = "Column separator must be present")
	private String columnSeparator = ",";

	@Schema(description = "Character separating lines.", example = "\\n")
	@NotNull(message = "Line separator must be present")
	private String lineSeparator = "\n";

	@Schema(description = "Character used for escaping values.", example = "\"")
	@NotNull(message = "Quote char must be present")
	private Character quoteChar = '"';

	@Schema(description = "Whether the file contains a header row.", example = "true")
	@NotNull(message = "Has header must be present")
	private Boolean hasHeader = true;
}
