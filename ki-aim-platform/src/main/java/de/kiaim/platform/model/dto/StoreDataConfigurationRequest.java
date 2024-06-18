package de.kiaim.platform.model.dto;

import de.kiaim.model.configuration.DataConfiguration;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.MediaType;

@Getter @Setter
public class StoreDataConfigurationRequest {

	@Parameter(description = "Metadata describing the format of the data as JSON or YAML.",
	           content = {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
	                               schema = @Schema(implementation = DataConfiguration.class)),
	                      @Content(mediaType = "application/x-yaml",
	                               schema = @Schema(implementation = DataConfiguration.class)),
	           })
	@NotNull(message = "Configuration must be present!")
	@Valid
	private DataConfiguration configuration;
}
