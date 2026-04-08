package de.kiaim.cinnamon.model.configuration.project;

/**
 * Importance of a metric.
 *
 * @author Daniel Preciado-Marquez
 */
public enum MetricImportance {
	/**
	 * Important metrics.
	 * Will be shown in the attribute overview.
	 */
	IMPORTANT,
	/**
	 * Additional metrics.
	 * Will be shown in the attribute details.
	 */
	ADDITIONAL,
	/**
	 * Metrics that are not relevant for the project.
	 * Will not be shown in any overview.
	 */
	NOT_RELEVANT,
}
