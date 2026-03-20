package de.kiaim.cinnamon.platform.exception;

import de.kiaim.cinnamon.model.dto.ErrorDetails;

/**
 * @author Daniel Preciado-Marquez
 */
public class BadConfigurationFileException extends BadRequestException {

	/**
	 * Exception code for providing an invalid YAML file.
	 */
	public final static String INVALID_YAML = "1";

	/**
	 * Exception code for providing an invalid YAML structure where the root element is not an object.
	 */
	public final static String ROOT_NOT_OBJECT = "2";

	/**
	 * Exception code for failed configuration import.
	 */
	public final static String IMPORT_FAILED = "3";

	public BadConfigurationFileException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	public BadConfigurationFileException(final String exceptionCode, final String message,
	                                     final ErrorDetails errorDetails) {
		super(exceptionCode, message, errorDetails);
	}

	public BadConfigurationFileException(final String exceptionCode, final String message, final Exception cause) {
		super(exceptionCode, message, cause);
	}

	@Override
	protected String getExceptionClassCode() {
		return CONFIGURATION_FILE;
	}
}
