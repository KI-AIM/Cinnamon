package de.kiaim.cinnamon.platform.model.file;

import de.kiaim.cinnamon.platform.model.validation.FileConfigurationSet;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Metadata describing the type of the uploaded data.")
@FileConfigurationSet
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileConfiguration {

	@Schema(description = "Type of the file.", example = "CSV")
	@NotNull(message = "File type must be present")
	private FileType fileType;

	@Schema(description = "Configurations specific for CSV files.")
	@Valid
	private CsvFileConfiguration csvFileConfiguration;

	@Schema(description = "Configuration specific for XLSX files")
	@Valid
	private XlsxFileConfiguration xlsxFileConfiguration;

	/**
	 * Configuration specific for FHIR bundles.
	 * Must be set if {@link #fileType} is set to {@link FileType#FHIR}.
	 */
	@Schema(description = "Configuration specific for FHIR bundles.")
	@Valid
	private FhirFileConfiguration fhirFileConfiguration;
}
