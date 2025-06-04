package de.kiaim.cinnamon.platform.model.enumeration;

/**
 * Enum for supported password constraints.
 *
 * @author Daniel Preciado-Marquez
 */
public enum PasswordConstraints {
	/**
	 * The password must contain at least one lower case character.
	 */
	LOWERCASE,
	/**
	 * The password must contain at least one digit.
	 */
	DIGIT,
	/**
	 * The password must contain at least one special character.
	 */
	SPECIAL_CHAR,
	/**
	 * The password must contain at least one upper case character.
	 */
	UPPERCASE,
}
