package de.kiaim.cinnamon.platform.model.enumeration;

/**
 * Roles a user can have.
 * The names are used directly as Spring Security authorities and therefore have to start with {@code ROLE_}.
 *
 * @author Daniel Preciado-Marquez
 */
public enum UserRole {
	/**
	 * Role every registered user has.
	 */
	ROLE_USER,

	/**
	 * Role granting access to the API.
	 */
	ROLE_API,

	/**
	 * Role granting access to the administration features.
	 */
	ROLE_ADMIN,

	/**
	 * Role granting access to the actuator endpoints and health details.
	 */
	ROLE_MONITORING,
	;
}
