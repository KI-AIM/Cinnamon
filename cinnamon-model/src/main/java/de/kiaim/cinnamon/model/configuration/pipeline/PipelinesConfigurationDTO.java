package de.kiaim.cinnamon.model.configuration.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.kiaim.cinnamon.model.configuration.ConfigurationDTO;
import de.kiaim.cinnamon.model.configuration.ConfigurationFile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for multiple pipelines.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Configuration for multiple pipelines.")
@NoArgsConstructor
@Getter @Setter
public class PipelinesConfigurationDTO implements ConfigurationDTO {

	/**
	 * List of pipelines to be configured.
	 * Currently, only one pipeline is supported.
	 */
	@Schema(description = "List of pipelines to be configured.")
	@NotNull @Size(min = 1, message = "At least one pipeline must be provided") @Valid
	private final List<PipelineConfigurationDTO> pipelines = new ArrayList<>();

	/**
	 * {@inheritDoc}
	 */
	@JsonIgnore
	@Override
	public String getKey() {
		return ConfigurationFile.PIPELINE_CONFIGURATION_KEY;
	}

}
