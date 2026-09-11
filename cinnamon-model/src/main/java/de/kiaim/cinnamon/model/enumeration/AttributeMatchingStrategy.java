package de.kiaim.cinnamon.model.enumeration;

import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;

/**
 * Enumeration for attribute matching strategies.
 *
 * @author Daniel Preciado-Marquez
 */
public enum AttributeMatchingStrategy {
	/**
	 * Match attributes by their name.
	 * Uses the header of the dataset and the {@link ColumnConfiguration#getName()}.
	 * If the dataset does not have a header, an error will be thrown.
	 * Ignores and overwrites the value of {@link ColumnConfiguration#getIndex()} during import.
	 */
	NAME,
	/**
	 * Match attributes by their index.
	 * Uses the order of the attributes in the dataset and the {@link ColumnConfiguration#getIndex()}.
	 * Ignores and overwrites the value of {@link ColumnConfiguration#getName()} during import.
	 */
	INDEX,
}
