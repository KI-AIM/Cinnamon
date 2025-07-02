package de.kiaim.cinnamon.platform.model.enumeration;

/**
 * Encoding for parts of a multipart request.
 *
 * @author Daniel Preciado-Marquez
 */
public enum StepInputEncoding {
	/**
	 * Writes the data into a CSV file and the data configuration into a separate YAML file.
	 */
	FILE,
	/**
	 * Writes the data and data configuration into one JSON file.
	 */
	JSON,
}
