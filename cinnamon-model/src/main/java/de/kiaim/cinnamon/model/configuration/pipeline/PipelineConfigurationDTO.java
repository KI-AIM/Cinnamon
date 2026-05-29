package de.kiaim.cinnamon.model.configuration.pipeline;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for a pipeline.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Configuration for a pipeline.")
@NoArgsConstructor
@Getter @Setter
public class PipelineConfigurationDTO {

	/**
	 * Set of jobs to be executed in the pipeline.
	 * The execution order is defined by the application and not by the pipeline configuration.
	 * Jobs not included in the set are not executed.
	 * <br>
	 * Uses a list for deterministic order during serialization.
	 */
	@Schema(description = "Set of jobs to be executed in the pipeline.")
	@NotNull @Valid
	private final List<JobConfigurationDTO> jobs = new ArrayList<>();

}
