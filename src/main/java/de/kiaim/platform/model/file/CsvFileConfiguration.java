package de.kiaim.platform.model.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor @NoArgsConstructor
@Getter @Setter
@Schema(description = "Configurations specific for CSV files.")
public class CsvFileConfiguration {

	@Schema(description = "Character separating the columns.", example = ",")
	private String columnSeparator = ",";

	@Schema(description = "Character separating lines.", example = "\n")
	private String lineSeparator = "\n";

	@Schema(description = "Character used for escaping values.", example = "\"")
	private char quoteChar = '"';

	@Schema(description = "Whether the file contains a header row.", example = "true")
	private boolean hasHeader = true;
}
