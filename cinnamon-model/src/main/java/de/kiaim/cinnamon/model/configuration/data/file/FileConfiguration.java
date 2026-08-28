package de.kiaim.cinnamon.model.configuration.data.file;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.kiaim.cinnamon.model.configuration.ConfigurationDTO;
import de.kiaim.cinnamon.model.configuration.ConfigurationFile;
import de.kiaim.cinnamon.model.validation.FileConfigurationSet;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Schema(description = "Metadata describing the type of the uploaded data.")
@JsonInclude(JsonInclude.Include.NON_NULL)
@FileConfigurationSet
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileConfiguration implements ConfigurationDTO {

	@Schema(description = "Type of the file.", example = "CSV")
	@NotNull(message = "File type must be present")
	private FileType fileType;

	/**
	 * Configuration specific for CSV files.
	 * Must be set if {@link #fileType} is set to {@link FileType#CSV}.
	 */
	@Schema(description = "Configurations specific for CSV files.")
	@Nullable
	private CsvFileConfiguration csvFileConfiguration;

	/**
	 * Configuration specific for XLSX files.
	 * Must be set if {@link #fileType} is set to {@link FileType#XLSX}.
	 */
	@Schema(description = "Configuration specific for XLSX files")
	@Nullable
	private XlsxFileConfiguration xlsxFileConfiguration;

	/**
	 * Configuration specific for FHIR bundles.
	 * Must be set if {@link #fileType} is set to {@link FileType#FHIR}.
	 */
	@Schema(description = "Configuration specific for FHIR bundles.")
	@Nullable
	private FhirFileConfiguration fhirFileConfiguration;

	/**
	 * {@inheritDoc}
	 */
	@JsonIgnore
	@Override
	public String getKey() {
		return ConfigurationFile.FILE_CONFIGURATION_KEY;
	}
}
