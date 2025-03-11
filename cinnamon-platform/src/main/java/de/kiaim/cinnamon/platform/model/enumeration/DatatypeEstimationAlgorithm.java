package de.kiaim.cinnamon.platform.model.enumeration;

/**
 * Algorithm how the datatype of a columns should be selected.
 */
public enum DatatypeEstimationAlgorithm {
	/**
	 * Selects the most occurring data type.
	 */
	MOST_ESTIMATED,
	/**
	 * Selects the most lenient data type that occurred.
	 */
	MOST_GENERAL,
	;
}
