package de.kiaim.platform.model.enumeration;

/**
 * Selector for a data set
 *
 * @author Daniel Preciado-Marquez
 */
public enum DataSetSelector {
	/**
	 * Selects the last data set that has been created in the current project.
	 * If none has been created, the original is selected instead.
	 */
	LAST_OR_ORIGINAL,
	/**
	 * Selects the original data set.
	 */
	ORIGINAL,
}
