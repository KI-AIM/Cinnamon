package de.kiaim.cinnamon.model.configuration;

/**
 * Marker interface for configuration DTOs.
 *
 * @author Daniel Preciado-Marquez
 */
public interface ConfigurationDTO {

	/**
	 * Returns the key of the configuration for the configuration file.
	 * @return the key of the configuration.
	 */
	String getKey();

	/**
	 * Whether the root of the serialized object contains the key of the configuration or not.
	 * @return true if the root contains the key, false otherwise.
	 */
	default boolean includesKey() {
		return false;
	}

}
