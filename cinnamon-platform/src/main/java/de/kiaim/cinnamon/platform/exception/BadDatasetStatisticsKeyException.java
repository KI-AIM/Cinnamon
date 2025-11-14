package de.kiaim.cinnamon.platform.exception;

/**
 * Exception for providing an unknown dataset statistics key.
 *
 * @author Daniel Preciado-Marquez
 */
public class BadDatasetStatisticsKeyException extends BadRequestException {

	/**
	 * Exception code for a dataset statistics key that was not configured in the application.properties.
	 */
	public static final String NOT_DEFINED = "1";

	/**
	 * Exception code for requesting a dataset statistics that is not calculated.
	 */
	public static final String NOT_FOUND = "2";

	/**
	 * Creates a new exception.
	 *
	 * @param exceptionCode The specific exception code.
	 * @param message       The exception message.
	 */
	public BadDatasetStatisticsKeyException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String getExceptionClassCode() {
		return DATASET_STATISTICS_KEY;
	}
}
