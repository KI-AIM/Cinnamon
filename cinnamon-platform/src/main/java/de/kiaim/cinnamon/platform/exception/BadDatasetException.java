package de.kiaim.cinnamon.platform.exception;

/**
 * Exceptions if the provided dataset is invalid and cannot be read.
 *
 * @author Daniel Preciado-Marquez
 */
public class BadDatasetException extends BadRequestException {

	/**
	 * Exception code for rows that contain too few values.
	 */
	public static final String ROW_TOO_FEW_VALUES = "1";

	/**
	 * Exception code for rows that contain too many values.
	 */
	public static final String ROW_TOO_MANY_VALUES = "2";

	public BadDatasetException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return DATASET;
	}
}
