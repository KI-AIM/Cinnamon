package de.kiaim.cinnamon.platform.model.enumeration;

import lombok.Getter;

/**
 * Delimiter for CSV files.
 *
 * @author Daniel Preciado-Marquez
 */
@Getter
public enum CsvDelimiter {
	COMMA(','),
	SEMICOLON(';'),
	TAB('\t'),
	VERTICAL_BAR('|'),
	;

	/**
	 * The delimiter character.
	 */
	private final char delimiter;

	/**
	 * Creates a new delimiter.
	 *
	 * @param delimiter The delimiter.
	 */
	CsvDelimiter(final char delimiter) {
		this.delimiter = delimiter;
	}

	/**
	 * Returns the string value of the delimiter.
	 *
	 * @return The string value.
	 */
	public String getDelimiterAsString() {
		return String.valueOf(delimiter);
	}
}
