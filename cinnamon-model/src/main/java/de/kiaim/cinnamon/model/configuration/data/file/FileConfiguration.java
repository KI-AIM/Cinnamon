package de.kiaim.cinnamon.model.configuration.data.file;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.kiaim.cinnamon.model.configuration.data.DataSourceServerConfiguration;
import de.kiaim.cinnamon.model.enumeration.DataSourceType;
import de.kiaim.cinnamon.model.validation.FileConfigurationSet;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

@Schema(description = "Metadata describing the type of the uploaded data.")
@JsonInclude(JsonInclude.Include.NON_NULL)
@FileConfigurationSet
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileConfiguration {

	/**
	 * Source of the file.
	 */
	@Schema(description = "Source of the file.")
	@NotNull(message = "Data source type must be present")
	private DataSourceType dataSourceType = DataSourceType.LOCAL;

	/**
	 * Configuration for the server where the file is located.
	 * Must be available if the {@link #dataSourceType} is {@link DataSourceType#SERVER}.
	 */
	@Schema(description = "Configuration for the server where the file is located.")
	@Nullable
	private DataSourceServerConfiguration server;

	@Schema(description = "Type of the file.", example = "CSV")
	@NotNull(message = "File type must be present")
	private FileType fileType;

	@Schema(description = "Configurations specific for CSV files.")
	private CsvFileConfiguration csvFileConfiguration;

	@Schema(description = "Configuration specific for XLSX files")
	private XlsxFileConfiguration xlsxFileConfiguration;

	/**
	 * Configuration specific for FHIR bundles.
	 * Must be set if {@link #fileType} is set to {@link FileType#FHIR}.
	 */
	@Schema(description = "Configuration specific for FHIR bundles.")
	private FhirFileConfiguration fhirFileConfiguration;
}
