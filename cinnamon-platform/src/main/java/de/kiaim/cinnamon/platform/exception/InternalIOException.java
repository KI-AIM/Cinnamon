package de.kiaim.cinnamon.platform.exception;

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

	/**
	 * Exception code for failed configuration serialization.
	 */
	public static final String CONFIGURATION_SERIALIZATION = "6";

	/**
	 * Exception code for failed data set serialization.
	 */
	public static final String DATA_SET_SERIALIZATION = "7";

	/**
	 * Exception code for failed XLSX file creation.
	 */
	public static final String XLSX_CREATION = "8";

	/**
	 * Exception code for failed FHIR bundle reading.
	 */
	public static final String FHIR_READING = "9";

	/**
	 * Exception code for failed CSV file reading.
	 */
	public static final String CSV_READING = "10";

	public InternalIOException(final String exceptionCode, final String message, final Exception cause) {
		super(exceptionCode, message, cause);
	}

	@Override
	protected String getExceptionClassCode() {
		return IO;
	}
}
