package de.kiaim.cinnamon.platform.exception;

/**
 * Exceptions that are cause because of invalid step names in requests.
 */
public class BadStepNameException extends BadRequestException {

	/**
	 * Exception code for step names that are not specified in the application.properties.
	 */
	public static final String NOT_FOUND = "1";

	/**
	 * Exception code for step names that are not in the stage defined by the context.
	 */
	public static final String NOT_IN_STAGE = "2";

	public BadStepNameException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return STEP_NAME;
	}
}
