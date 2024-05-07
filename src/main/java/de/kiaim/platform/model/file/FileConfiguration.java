package de.kiaim.platform.model.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Metadata describing the type of the uploaded data.")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileConfiguration {

	@Schema(description = "Type of the file.", example = "CSV")
	private FileType fileType;

	@Schema(description = "Configurations specific for CSV files.")
	private CsvFileConfiguration csvFileConfiguration;
}
