package de.kiaim.cinnamon.model.configuration;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import de.kiaim.cinnamon.model.configuration.data.DataSourceConfiguration;
import de.kiaim.cinnamon.model.configuration.data.DatasetConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.model.configuration.data.file.FileConfiguration;
import de.kiaim.cinnamon.model.configuration.pipeline.PipelinesConfigurationDTO;
import de.kiaim.cinnamon.model.configuration.project.ProjectConfigurationDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration file of the platform.
 * Contains all configurations used in the Cinnamon platform.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Configuration file of the platform.")
@NoArgsConstructor
@Getter @Setter
public class ConfigurationFile {

	/**
	 * Key for the data configuration (see {@link DataConfiguration}).
	 * Matches the name of the field {@link DataConfiguration#getConfigurations()}.
	 */
	public static final String DATA_CONFIGURATION_KEY = "configurations";

	/**
	 * Key for the project configuration (see {@link ProjectConfigurationDTO}).
	 * Matches the name of the field {@link #getProject()}.
	 */
	public static final String PROJECT_CONFIGURATION_KEY = "project";

	/**
	 * Key for the data source configuration (see {@link DataSourceConfiguration}).
	 * Matches the name of the field {@link #getDataSource()}.
	 */
	public static final String DATA_SOURCE_CONFIGURATION_KEY = "dataSource";

	/**
	 * Key for the file configuration (see {@link FileConfiguration}).
	 * Matches the name of the field {@link #getFile()}.
	 */
	public static final String FILE_CONFIGURATION_KEY = "file";

	/**
	 * Key for the dataset configuration (see {@link FileConfiguration}).
	 */
	public static final String DATASET_CONFIGURATION_KEY = "dataset";

	/**
	 * Key for the pipeline configuration (see {@link PipelinesConfigurationDTO}).
	 * Matches the name of the field {@link #getPipeline()}.
	 */
	public static final String PIPELINE_CONFIGURATION_KEY = "pipeline";

	/**
	 * Configuration for general project settings.
	 */
	@Schema(description = "Configuration for general project settings.")
	@Valid
	@Nullable
	private ProjectConfigurationDTO project;

	/**
	 * Configuration of the data source.
	 */
	@Schema(description = "Configuration of the data source.")
	@Valid
	@Nullable
	private DataSourceConfiguration dataSource;

	/**
	 * Configuration of the file.
	 */
	@Schema(description = "Configuration of the file.")
	@Valid
	@Nullable
	private FileConfiguration file;

	/**
	 * Definition of the attributes to be imported.
	 */
	@Schema(description = "Definition of the attributes to be imported.")
	@Valid
	@Nullable
	private DataConfiguration attributes;

	/**
	 * Configuration of dataset properties not related to a single attribute.
	 */
	@Schema(description = "Configuration of dataset properties not related to a single attribute.")
	@Valid
	@Nullable
	private DatasetConfiguration dataset;

	/**
	 * Configuration for the pipelines.
	 */
	@Schema(description = "Configuration for the pipelines.")
	@Valid
	@Nullable
	private PipelinesConfigurationDTO pipeline;

	/**
	 * Configurations for external modules.
	 */
	@Schema(description = "Configurations for external modules.")
	@JsonAnyGetter @JsonAnySetter
	@Valid
	private Map<String, ConfigurationPart> parts = new HashMap<>();
}
