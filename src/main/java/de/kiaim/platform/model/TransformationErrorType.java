package de.kiaim.platform.model;

/**
 * Enum of errors that can occur during the transformation into a DataSet.
 */
public enum TransformationErrorType {
	/**
	 * The value is missing.
	 */
	MISSING_VALUE,
	/**
	 * The value is not in the specified format.
	 */
	FORMAT_ERROR,

	/**
	 * Error for faulty configurations
	 */

	CONFIG_ERROR,

	/**
	 * For every error that is not resolved
	 */
	OTHER
}
