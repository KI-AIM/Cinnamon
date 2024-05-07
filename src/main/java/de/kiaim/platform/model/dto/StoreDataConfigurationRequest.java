package de.kiaim.platform.model.dto;

import de.kiaim.platform.model.data.configuration.DataConfiguration;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class StoreDataConfigurationRequest {

	@Schema(implementation = DataConfiguration.class)
	@NotNull(message = "Configuration must be present!")
	@Valid
	private DataConfiguration configuration;
}
