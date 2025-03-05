package de.kiaim.platform.exception;

/**
 * Exceptions for requesting resources that could not be found.
 *
 * @author Daniel Preciado-Marquez
 */
public class BadQueryException extends BadRequestException {

	/**
	 * Exception code when requesting a file resulting from a process.
	 */
	public static final String RESULT_FILE = "1";

	public BadQueryException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return QUERY;
	}
}
