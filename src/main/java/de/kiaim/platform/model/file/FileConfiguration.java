package de.kiaim.platform.model.file;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
	@NotNull(message = "File type must be present")
	private FileType fileType;

	@Schema(description = "Configurations specific for CSV files.")
	@NotNull(message = "CSV file configuration must be present")
	@Valid
	private CsvFileConfiguration csvFileConfiguration;

	@Schema(description = "Configuration specific for XLSX files")
	private XlsxFileConfiguration xlsxFileConfiguration;
}
