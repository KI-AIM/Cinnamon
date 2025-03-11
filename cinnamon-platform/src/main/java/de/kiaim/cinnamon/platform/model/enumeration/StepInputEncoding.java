package de.kiaim.cinnamon.platform.model.enumeration;

/**
 * Encoding for parts of a multipart request.
 *
 * @author Daniel Preciado-Marquez
 */
public enum StepInputEncoding {
	/**
	 * Writes the bytes of the data into a file.
	 */
	FILE,
	/**
	 * Converts the data into JSON.
	 */
	JSON,
}
