package de.kiaim.cinnamon.platform.model.enumeration;

import lombok.Getter;

/**
 * Quote character for CSV files.
 *
 * @author Daniel Preciado-Marquez
 */
@Getter
public enum CsvQuote {
	DOUBLE_QUOTE('"'),
	SINGLE_QUOTE('\''),
	;

	/**
	 * The quote character value.
	 */
	private final Character quote;

	/**
	 * Creates a new quote char.
	 *
	 * @param quote The character value.
	 */
	CsvQuote(final Character quote) {
		this.quote = quote;
	}
}
