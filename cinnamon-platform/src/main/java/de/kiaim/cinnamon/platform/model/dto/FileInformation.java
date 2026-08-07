package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.model.configuration.data.file.FileType;
import de.kiaim.cinnamon.model.enumeration.DataSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.util.Set;

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

	/**
	 * Type of the source where the file was obtained.
	 * Null if no data source was configured for the file.
	 */
	@Schema(description = "Type of the source where the file was obtained. Null if no data source was configured for the file.")
	@Nullable
	private DataSourceType dataSourceType;

	/**
	 * List the resource types contained in the FHIR bundle,
	 * if the estimated file configuration has type {@link FileType#FHIR}
	 * Otherwise the value is null.
	 */
	@Schema(description = "Resource types of the FHIR bundle. Null if the file was not a FHIR bundle.",
	        example = "[Patient, Observation]")
	@Nullable
	private Set<String> fhirResourceTypes;

	@Schema(description = "Number of attributes in the file.", example = "CSV")
	private int numberOfAttributes;
}
