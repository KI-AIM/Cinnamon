package de.kiaim.cinnamon.platform.model.enumeration;

import lombok.Getter;

/**
 * Languages supported by the platform.
 * Adding a new constant is sufficient to make the language available for email templates,
 * both in the API and in the administration UI.
 *
 * @author Daniel Preciado-Marquez
 */
@Getter
public enum SupportedLanguage {
	ENGLISH("English"),
	GERMAN("German"),
	;

	/**
	 * Name of the language as it is presented to the user.
	 */
	private final String displayName;

	SupportedLanguage(final String displayName) {
		this.displayName = displayName;
	}
}
