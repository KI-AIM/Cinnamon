package de.kiaim.cinnamon.platform.model.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

/**
 * Configuration for the initial administrator account.
 * If a username and a password are configured, the account is created on startup if it does not exist yet.
 *
 * @author Daniel Preciado-Marquez
 */
@Getter @Setter
public class AdminConfiguration {

	/**
	 * The username of the initial administrator.
	 * If not set, no administrator is created on startup.
	 */
	@Nullable
	private String username = null;

	/**
	 * The password of the initial administrator.
	 * Is only used when the account is created and must meet the configured password requirements.
	 */
	@Nullable
	private String password = null;

	/**
	 * Returns if an initial administrator has been configured.
	 *
	 * @return True if a username has been configured, false otherwise.
	 */
	public boolean isConfigured() {
		return username != null && !username.isBlank();
	}
}
