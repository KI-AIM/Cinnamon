package de.kiaim.cinnamon.model.enumeration;

/**
 * Describes the outcome of the attribute matching of a single attribute.
 *
 * @author Daniel Preciado-Marquez
 */
public enum AttributeMatchOutcome {
	/**
	 * The attribute is present in the data and in the configuration.
	 */
	MATCHED,
	/**
	 * The attribute is only present in the data.
	 */
	DATA_ONLY,
	/**
	 * The attribute is only present in the configuration.
	 */
	CONFIG_ONLY,
}
