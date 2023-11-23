package de.kiaim.platform.model.data.configuration;

/**
 * Empty interface that all Data configurations
 * should implement in order to be dynamically
 * processed
 */
public interface Configuration {

	/**
	 * Returns the name of the concrete implementation.
	 * Used for Jackson serialization.
	 *
	 * @return Name of the Configuration.
	 */
	String getName();
}
