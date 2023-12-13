package de.kiaim.platform.model.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileConfiguration {

	@Schema(description = "Type of the file.", example = "CSV")
	private FileType fileType;

	//===========
	//--- CSV ---
	//===========

	@Schema(description = "Character separating the columns.", example = ",")
	private String columnSeparator = ",";

	@Schema(description = "Character separating lines.", example = "\n")
	private String lineSeparator = "\n";

	@Schema(description = "Whether the file contains a header row.", example = "true")
	private boolean hasHeader = true;
}
