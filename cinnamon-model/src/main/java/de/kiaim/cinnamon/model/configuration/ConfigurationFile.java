package de.kiaim.cinnamon.model.configuration;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.configuration.project.ProjectConfigurationDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
	 * Configuration for general project settings.
	 */
	@Schema(description = "Configuration for general project settings.")
	@Valid
	@Nullable
	private ProjectConfigurationDTO project;

	/**
	 * The data configuration of the original dataset.
	 */
	@Schema(description = "The data configuration of the original dataset.")
	@Valid
	@Nullable
	private DataConfiguration data = null;

	/**
	 * Configurations for external modules.
	 */
	@Schema(description = "Configurations for external modules.")
	@JsonAnyGetter @JsonAnySetter
	@Valid
	private Map<String, ConfigurationPart> parts = new HashMap<>();
}
