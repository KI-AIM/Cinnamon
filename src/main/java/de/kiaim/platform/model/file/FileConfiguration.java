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

	@Schema(description = "Configurations specific for CSV files.")
	private CsvFileConfiguration csvFileConfiguration;

	@Schema(description = "Configuration specific for XLSX files")
	private XlsxFileConfiguration xlsxFileConfiguration;
}
