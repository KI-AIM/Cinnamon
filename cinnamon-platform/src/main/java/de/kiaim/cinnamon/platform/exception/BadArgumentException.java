package de.kiaim.cinnamon.platform.exception;

/**
 * Exception for invalid arguments.
 *
 * @author Daniel Preciado-Marquez
 */
public class BadArgumentException extends BadRequestException {

	/**
	 * Exception code for invalid hold-out percentage.
	 */
	public static final String HOLD_OUT_PERCENTAGE = "1";

	/**
	 * Exception code for an invalid resource key.
	 */
	public static final String INVALID_RESOURCE_KEY = "2";

	/**
	 * Exception code for an invalid workflow ID.
	 */
	public static final String INVALID_PROJECT_ID = "3";

	/**
	 * Exception code for an invalid invitation ID.
	 */
	public static final String INVALID_INVITATION_ID = "4";

	public BadArgumentException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return ARGUMENT;
	}
}
