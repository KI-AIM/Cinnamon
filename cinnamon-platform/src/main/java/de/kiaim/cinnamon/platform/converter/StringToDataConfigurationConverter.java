package de.kiaim.cinnamon.platform.converter;

import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Converts a string to a {@link DataConfiguration} in incoming requests.
 *
 * @author Daniel Preciado-Marquez
 */
@Component
public class StringToDataConfigurationConverter extends CinnamonStringConverter<DataConfiguration> {
	@Autowired
	public StringToDataConfigurationConverter(final SerializationConfig serializationConfig) {
		super(DataConfiguration.class, serializationConfig);
	}
}
