package de.kiaim.platform.model.dto;

import de.kiaim.platform.model.file.FileType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

@Schema(description = "General information about a file.")
@NoArgsConstructor @AllArgsConstructor
@Getter @Setter
public class FileInformation {
	@Schema(description = "Name of the file.", example = "data.csv")
	@Nullable
	private String name;

	@Schema(description = "File type of the file.", example = "CSV")
	@Nullable
	private FileType type;

	@Schema(description = "Number of attributes in the file.", example = "CSV")
	@Nullable
	private Integer numberOfAttributes;
}
