package de.kiaim.cinnamon.platform.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.kiaim.cinnamon.platform.model.configuration.PasswordRequirementsConfiguration;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * General information about Cinnamon.
 *
 * @author Daniel Preciado-Marquez
 */
@Data
@Schema(description = "General information about Cinnamon.")
public class CinnamonInfo {

	/**
	 * Whether the Cinnamon server is configured as a demo instance.
	 */
	@JsonIgnore
	private final boolean isDemoInstance;

	/**
	 * Getter to prevent Jackson removing the 'is' in the name without adding another property.
	 *
	 * @return Whether the Cinnamon server is configured as a demo instance.
	 */
	@Schema(description = "Whether the Cinnamon server is configured as a demo instance.", example = "false")
	@JsonProperty("isDemoInstance")
	public boolean getIsDemoInstance() {
		return isDemoInstance;
	}

	/**
	 * The maximum file size for uploaded files.
	 */
	@Schema(description = "The maximum file size for uploaded files.", example = "10485760")
	private final long maxFileSize;

	/**
	 * The password requirements.
	 */
	@Schema(description = "The password requirements.")
	private final PasswordRequirementsConfiguration passwordRequirements;

	/**
	 * The Cinnamon version.
	 */
	@Schema(description = "The Cinnamon version.", example = "1.0.0")
	private final String version;
}
