package de.kiaim.platform.exception;

/**
 * Exception for invalid application states.
 */
public class InternalInvalidStateException extends InternalException {

	/**
	 * Exception code for missing {@link de.kiaim.platform.model.entity.ExternalProcessEntity} for a step that requires external processing.
	 */
	public static final String MISSING_PROCESS_ENTITY = "1";

	public InternalInvalidStateException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return INVALID_STATE;
	}
}
