package de.kiaim.cinnamon.platform.exception;

import de.kiaim.cinnamon.platform.model.entity.ExternalProcessEntity;

/**
 * Exception for invalid application states.
 */
public class InternalInvalidStateException extends InternalException {

	/**
	 * Exception code for missing {@link ExternalProcessEntity} for a step that requires external processing.
	 */
	public static final String MISSING_PROCESS_ENTITY = "1";

	/**
	 * Exception code for missing configurations that are required to start a process.
	 */
	public static final String MISSING_CONFIGURATION = "2";

	/**
	 * Exception code for trying to start the next process if the previous process is not finished or skipped.
	 */
	public static final String LAST_STEP_NOT_FINISHED = "3";

	/**
	 * Exception code for a missing data set that should be present based on the status of the project.
	 */
	public static final String MISSING_DATA_STET = "4";

	/**
	 * Exception code for a missing {@link de.kiaim.cinnamon.platform.model.entity.ExecutionStepEntity} that should be
	 * present in the pipeline based on the status of the project.
	 */
	public static final String MISSING_STAGE = "5";

	/**
	 * Exception code for unset server instances.
	 */
	public static final String NO_SERVER_INSTANCE_SET = "6";

	public InternalInvalidStateException(final String exceptionCode, final String message) {
		super(exceptionCode, message);
	}

	@Override
	protected String getExceptionClassCode() {
		return INVALID_STATE;
	}
}
