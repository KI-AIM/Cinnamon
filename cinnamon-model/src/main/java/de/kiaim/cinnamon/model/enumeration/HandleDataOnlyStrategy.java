package de.kiaim.cinnamon.model.enumeration;

/**
 * Enumeration for strategies to handle attributes that are only present in the imported dataset and not in the
 * configuration during the import process.
 *
 * @author Daniel Preciado-Marquez
 */
public enum HandleDataOnlyStrategy {
	/**
	 * Throw an error if there are attributes in the imported dataset that are not present in the configuration.
	 */
	ERROR,
	/**
	 * Estimate the configuration for attributes that are only present in the imported dataset.
	 */
	ESTIMATE_CONFIG,
	/**
	 * Remove attributes that are only present in the imported dataset.
	 */
	REMOVE_ATTRIBUTE,
}
