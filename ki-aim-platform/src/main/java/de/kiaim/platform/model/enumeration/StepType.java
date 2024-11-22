package de.kiaim.platform.model.enumeration;

/**
 * The type of processing step.
 * Used to determine the entity used in the database.
 *
 * @author Daniel Preciado-Marquez
 */
public enum StepType {
	/**
	 * Step that returns a new data set.
	 */
	DATA_PROCESSING,
	/**
	 * Step that return some evaluation.
	 */
	EVALUATION,
	;
}
