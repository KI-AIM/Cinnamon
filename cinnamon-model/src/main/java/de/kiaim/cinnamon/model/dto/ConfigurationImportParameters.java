package de.kiaim.cinnamon.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

import java.util.Set;

/**
 * Parameters for importing configurations from a configuration file.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Parameters for importing configurations from a configuration file.")
@NoArgsConstructor
@Getter @Setter
public class ConfigurationImportParameters {
	/**
	 * If a partial import is allowed if the import of one configuration fails.
	 */
	@Schema(description = "If a partial import is allowed if the import of one configuration fails.")
	private boolean allowPartialImport = true;

	/**
	 * List of configuration names to import.
	 * If null, all configurations are imported.
	 */
	@Schema(description = "List of configuration names to import. If null, all configurations are imported.",
	        example = "[anonymization, synthetization_configuration]")
	@Nullable
	private Set<String> configurationsToImport = null;
}
