package de.kiaim.platform.model.enumeration;

/**
 * Defines the source of the data set.
 *
 * @author Daniel Preciado-Marquez
 */
public enum DataSetSourceSelector {
	/**
	 * Selects the original data set of the project.
	 */
	ORIGINAL,
	/**
	 * Selects the data set resulting of a job.
	 * Needs further specification of which job.
	 */
	JOB,
}
