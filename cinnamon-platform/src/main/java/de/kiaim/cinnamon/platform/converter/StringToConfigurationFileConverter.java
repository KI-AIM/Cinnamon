package de.kiaim.cinnamon.platform.converter;

import de.kiaim.cinnamon.model.configuration.ConfigurationFile;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Converts a string to a {@link ConfigurationFile} in incoming requests.
 *
 * @author Daniel Preciado-Marquez
 */
@Component
public class StringToConfigurationFileConverter extends CinnamonStringConverter<ConfigurationFile> {
	@Autowired
	public StringToConfigurationFileConverter(final SerializationConfig serializationConfig) {
		super(ConfigurationFile.class, serializationConfig);
	}
}
