package de.kiaim.platform.exception;

/**
 * Exception wrapper for IOExceptions.
 */
public class InternalIOException extends InternalException {

	/**
	 * Exception code for failed ZIP file creation.
	 */
	public static final String ZIP_CREATION = "1";

	/**
	 * Exception code for failed multipart file reading.
	 */
	public static final String MULTIPART_READING = "2";

	/**
	 * Exception code for failed data configuration deserialization from JSON stored in the database.
	 */
	public static final String DATA_CONFIGURATION_DESERIALIZATION = "3";

	/**
	 * Exception code for failed data configuration serialization.
	 */
	public static final String DATA_CONFIGURATION_SERIALIZATION = "4";

	/**
	 * Exception code for failed csv file creation.
	 */
	public static final String CSV_CREATION = "5";

	public InternalIOException(final String exceptionCode, final String message, final Exception cause) {
		super(exceptionCode, message, cause);
	}

	@Override
	protected String getExceptionClassCode() {
		return IO;
	}
}
