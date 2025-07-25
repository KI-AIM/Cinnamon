package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.entity.ProjectConfigurationEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

/**
 * DTO for {@link ProjectConfigurationEntity}.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Configurations for the project.")
@Getter @Setter
public class ProjectConfigurationDTO {

	/**
	 * Name of the dataset.
	 */
	@Schema(description = "Name of the project.", example = "Cinnamon ")
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
	private Object metricConfiguration;
}
