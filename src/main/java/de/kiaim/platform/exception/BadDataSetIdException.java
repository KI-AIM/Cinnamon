package de.kiaim.platform.exception;

/**
 * Exceptions that are cause because of invalid data set IDs in requests.
 */
public class BadDataSetIdException extends BadRequestException {

	/**
	 * Exception code for not existing data set IDs, i.e. no data configuration has been stored.
	 */
	public static final String NO_CONFIGURATION = "1";

	/**
	 * Exception code for data set IDs that have no corresponding data set.
	 */
	public static final String NO_DATA_SET = "2";

	/**
	 * Exception code for data set IDs that already have a corresponding data set stored.
	 */
	public static final String ALREADY_STORED = "3";

	public BadDataSetIdException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return DATA_SET_ID;
	}
}
