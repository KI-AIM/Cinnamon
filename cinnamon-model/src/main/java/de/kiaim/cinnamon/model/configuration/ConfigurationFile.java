package de.kiaim.cinnamon.model.configuration;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration file of the platform.
 * Contains all configurations used in the Cinnamon platform.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Configuration file of the platform.")
@NoArgsConstructor
@Getter @Setter
public class ConfigurationFile {

	/**
	 * The data configuration of the original dataset.
	 */
	@Schema(description = "The data configuration of the original dataset.")
	@Valid
	@Nullable
	private DataConfiguration data = null;

	/**
	 * Configurations for external modules.
	 */
	@Schema(description = "Configurations for external modules.")
	@JsonAnyGetter @JsonAnySetter
	@Valid
	private Map<String, ConfigurationPart> parts = new HashMap<>();
}
