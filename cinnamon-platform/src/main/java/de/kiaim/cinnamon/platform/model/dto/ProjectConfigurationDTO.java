package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.platform.model.entity.ProjectConfigurationEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO for {@link ProjectConfigurationEntity}.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Configurations for the project.")
@Getter @Setter
public class ProjectConfigurationDTO {

	/**
	 * Metric importance.
	 */
	@Schema(description = "Priority of the metrics.")
	@NotNull(message = "The metric configuration must be present!")
	private Object metricConfiguration;
}
