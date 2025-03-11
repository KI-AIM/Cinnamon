package de.kiaim.cinnamon.platform.exception;

/**
 * Exception for invalid arguments.
 *
 * @author Daniel Preciado-Marquez
 */
public class BadArgumentException extends BadRequestException {

	/**
	 * Exception code for invalid hold out percentage.
	 */
	public static final String HOLD_OUT_PERCENTAGE = "1";


	public BadArgumentException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return ARGUMENT;
	}
}
