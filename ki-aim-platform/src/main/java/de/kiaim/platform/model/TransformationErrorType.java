package de.kiaim.platform.model;

/**
 * Enum of errors that can occur during the transformation into a DataSet.
 */
public enum TransformationErrorType {

	/**
	 * Error for faulty configurations
	 */
	CONFIG_ERROR,

	/**
	 * The value is not in the specified format.
	 */
	FORMAT_ERROR,

	/**
	 * The value is missing.
	 */
	MISSING_VALUE,

	/**
	 * The value is not inside the specified range.
	 */
	VALUE_NOT_IN_RANGE,

	/**
	 * For every error that is not resolved
	 */
	OTHER
}
