package de.kiaim.platform.exception;

/**
 * Exceptions that are cause because of invalid files containing the data.
 */
public class BadFileException extends BadRequestException {

	/**
	 * Exception code for unsupported file types.
	 */
	public static final String UNSUPPORTED = "1";

	/**
	 * Exception code for files that could not be read.
	 */
	public static final String NOT_READABLE = "2";

	/**
	 * Exception code for requests that should, but do not contain a file.
	 */
	public static final String MISSING_FILE = "3";

	/**
	 * Exception code for files that have no file name.
	 */
	public static final String MISSING_FILE_NAME = "4";

	/**
	 * Exception code for files that have no file extension.
	 */
	public static final String MISSING_FILE_EXTENSION = "5";

	public BadFileException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return FILE;
	}
}
