package de.kiaim.cinnamon.platform.exception;

/**
 * Exception for invalid method results due to a bug in the code.
 */
public class InternalInvalidResultException extends InternalException {

	/**
	 * Exception code for an invalid data configuration created by the estimation.
	 */
	public static final String INVALID_ESTIMATION = "1";

	public InternalInvalidResultException(final String exceptionCode, final String message, final Exception cause) {
		super(exceptionCode, message, cause);
	}

	@Override
	protected String getExceptionClassCode() {
		return INVALID_RESULT;
	}
}
