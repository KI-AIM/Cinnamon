package de.kiaim.cinnamon.model.configuration.pipeline;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for configuring a job.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Configuration of a job.")
@NoArgsConstructor
@Getter @Setter
public class JobConfigurationDTO {

	/**
	 * The name of the job this configuration belongs to.
	 * The job names are specified in the platform's application properties.
	 */
	@Schema(description = "The name of the job this configuration belongs to.", example = "anonymization")
	@NotBlank
	private String name;

	/**
	 * Whether the job should be executed or not.
	 */
	@Schema(description = "Whether the job should be executed or not.", example = "true")
	@NotNull
	private Boolean enabled = true;

	/**
	 * The algorithm configuration to be used for executing the job.
	 * Currently, there is always only one algorithm configuration available.
	 */
	@Schema(description = "The algorithm configuration to be used for executing the job.", example = "0")
	@NotNull @Min(0)
	private Integer configuration = 0;

}
