package de.kiaim.cinnamon.platform.converter;

import de.kiaim.cinnamon.model.configuration.data.file.FileConfiguration;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import org.springframework.stereotype.Component;

/**
 * Converts a string to a {@link FileConfiguration} in incoming requests.
 *
 * @author Daniel Preciado-Marquez
 */
@Component
public class StringToFileConfigurationConverter extends CinnamonStringConverter<FileConfiguration> {
	public StringToFileConfigurationConverter(final SerializationConfig serializationConfig) {
		super(FileConfiguration.class, serializationConfig);
	}
}
