package de.kiaim.cinnamon.platform.model.configuration;

import de.kiaim.cinnamon.platform.model.enumeration.PasswordConstraints;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for password requirements.
 *
 * @author Daniel Preciado-Marquez
 */
@Getter @Setter
public class PasswordRequirementsConfiguration {

	/**
	 * The minimum length of passwords.
	 */
	private int minLength = 0;

	/**
	 * Additional constraints for passwords. See {@link PasswordConstraints} for available options.
	 */
	private Set<PasswordConstraints> constraints = new HashSet<>();
}
