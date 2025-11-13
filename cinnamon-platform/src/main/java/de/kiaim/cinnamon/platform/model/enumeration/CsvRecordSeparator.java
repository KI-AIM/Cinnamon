package de.kiaim.cinnamon.platform.model.enumeration;

import lombok.Getter;

/**
 * Record separator for CSV files.
 *
 * @author Daniel Preciado-Marquez
 */
@Getter
public enum CsvRecordSeparator {
	CR("\r", 1),
	CRLF("\r\n", 0),
	LF("\n", 2),
	;

	/**
	 * The separator value.
	 */
	private final String separator;

	/**
	 * Number defining the order in which the separators should be detected in a file.
	 * Important because some separators are contained in others.
	 */
	private final int detectionOrder;

	/**
	 * Creates a new record separator.
	 *
	 * @param separator      The string value.
	 * @param detectionOrder The detection order used for detecting the string value in a file.
	 */
	CsvRecordSeparator(final String separator, final int detectionOrder) {
		this.separator = separator;
		this.detectionOrder = detectionOrder;
	}
}
