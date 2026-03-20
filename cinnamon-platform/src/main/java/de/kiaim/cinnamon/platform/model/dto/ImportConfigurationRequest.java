package de.kiaim.cinnamon.platform.model.dto;

import de.kiaim.cinnamon.model.dto.ConfigurationImportParameters;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * Request for importing a configuration file.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Request for importing a configuration file.")
@NoArgsConstructor
@Getter @Setter
public class ImportConfigurationRequest {
	/**
	 * The configuration YAML file to import.
	 * The root element must be an object containing the configurations with the configured name as the key.
	 */
	@Schema(description = "The configuration YAML file to import. The root element must be an object containing the configurations with the configured name as the key.")
	@NotNull
	private MultipartFile configuration;

	/**
	 * Parameters defining the configurations to import.
	 */
	@Schema(description = "Parameter defining the configurations to import.",
	        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
	private ConfigurationImportParameters importParameters = new ConfigurationImportParameters();
}
