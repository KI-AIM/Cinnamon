package de.kiaim.cinnamon.platform.model.configuration;

import de.kiaim.cinnamon.platform.model.enumeration.PasswordConstraints;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for password requirements.
 *
 * @author Daniel Preciado-Marquez
 */
@Schema(description = "Configuration for password requirements.")
@Getter @Setter
public class PasswordRequirementsConfiguration {

	/**
	 * The minimum length of passwords.
	 */
	@Schema(description = "The minimum length of passwords.", example = "8")
	private int minLength = 0;

	/**
	 * Additional constraints for passwords. See {@link PasswordConstraints} for available options.
	 */
	@Schema(description = "Additional constraints for passwords.")
	private Set<PasswordConstraints> constraints = new HashSet<>();
}
