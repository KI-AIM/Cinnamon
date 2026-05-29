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

	/**
	 * Exception code for failed project configuration deserialization.
	 */
	public static final String PROJECT_CONFIGURATION_DESERIALIZATION = "4";

	/**
	 * Exception code for failed pipelines configuration deserialization.
	 */
	public static final String PIPELINES_CONFIGURATION_DESERIALIZATION = "5";

	/**
	 * Exception code for failed data source configuration deserialization.
	 */
	public static final String DATA_SOURCE_CONFIGURATION_DESERIALIZATION = "6";

	/**
	 * Exception code for failed data configuration serialization.
	 */
	public static final String DATA_CONFIGURATION_DESERIALIZATION = "7";

	/**
	 * Exception code for failed dataset configuration deserialization.
	 */
	public static final String DATASET_CONFIGURATION_DESERIALIZATION = "8";

	/**
	 * Exception code for failed configuration deserialization.
	 */
	public static final String CONFIGURATION_DESERIALIZATION = "9";

	/**
	 * Exception code for files that could not be read.
	 */
	public static final String NOT_READABLE = "10";

	/**
	 * Exception code for requests that should, but do not contain a configuration file.
	 */
	public static final String MISSING = "11";

	/**
	 * Exception code for empty configuration files.
	 */
	public static final String EMPTY = "12";

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
