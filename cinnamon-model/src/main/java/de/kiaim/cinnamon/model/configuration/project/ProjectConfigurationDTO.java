package de.kiaim.cinnamon.model.configuration.project;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.kiaim.cinnamon.model.configuration.ConfigurationDTO;
import de.kiaim.cinnamon.model.configuration.ConfigurationFile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

/**
 * Configurations for the project.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Configurations for the project.")
@Getter @Setter
@EqualsAndHashCode
public class ProjectConfigurationDTO implements ConfigurationDTO {

	/**
	 * Name of the dataset.
	 */
	@Schema(description = "Name of the project.", example = "Cinnamon ")
	@NotBlank
	private String projectName;

	/**
	 * Contact mail address.
	 */
	@Schema(description = "Mail address of the contact person.", example = "contact@example.com")
	@Nullable
	private String contactMail;

	/**
	 * Website.
	 */
	@Schema(description = "URL of the website.", example = "https://www.example.com")
	@Nullable
	private String contactUrl;

	/**
	 * Name of report creator.
	 */
	@Schema(description = "Name of the report creator.", defaultValue = "M. Mustermann")
	@Nullable
	private String reportCreator;

	/**
	 * Metric importance.
	 */
	@Schema(description = "Priority of the metrics.")
	@NotNull(message = "The metric configuration must be present!")
	@Valid
	private MetricConfiguration metricConfiguration = new MetricConfiguration();

	/**
	 * {@inheritDoc}
	 */
	@JsonIgnore
	@Override
	public String getKey() {
		return ConfigurationFile.PROJECT_CONFIGURATION_KEY;
	}
}
