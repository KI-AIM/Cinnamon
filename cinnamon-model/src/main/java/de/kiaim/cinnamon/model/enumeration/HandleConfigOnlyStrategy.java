package de.kiaim.cinnamon.model.enumeration;

/**
 * Enumeration for strategies to handle configuration-only attributes during the import process.
 *
 * @author Daniel Preciado-Marquez
 */
public enum HandleConfigOnlyStrategy {
	/**
	 * Throw an error if there are attributes in the configuration that are not present in the imported dataset.
	 */
	ERROR,
	/**
	 * Ignore attributes in the configuration that are not present in the imported dataset.
	 */
	IGNORE,
}
