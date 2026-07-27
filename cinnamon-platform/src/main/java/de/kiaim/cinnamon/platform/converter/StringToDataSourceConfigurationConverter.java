package de.kiaim.cinnamon.platform.converter;

import de.kiaim.cinnamon.model.configuration.data.DataSourceConfiguration;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import org.springframework.stereotype.Component;

/**
 * Converts a string to a {@link DataSourceConfiguration} in incoming requests.
 *
 * @author Daniel Preciado-Marquez
 */
@Component
public class StringToDataSourceConfigurationConverter extends CinnamonStringConverter<DataSourceConfiguration> {
	public StringToDataSourceConfigurationConverter(final SerializationConfig serializationConfig) {
		super(DataSourceConfiguration.class, serializationConfig);
	}
}
