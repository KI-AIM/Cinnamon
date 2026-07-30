package de.kiaim.cinnamon.platform.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

/**
 * DTO for storing a configuration.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Request for configuring an external process.")
@Getter @Setter
@NoArgsConstructor
public class ConfigurationRequest {

	@Schema(description = "Content of the configuration. Can be any string.")
	@Nullable
	private String configuration;
}
