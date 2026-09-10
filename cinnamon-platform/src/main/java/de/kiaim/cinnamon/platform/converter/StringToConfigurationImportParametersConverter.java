package de.kiaim.cinnamon.platform.converter;

import de.kiaim.cinnamon.model.dto.ConfigurationImportParameters;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import org.springframework.stereotype.Component;

/**
 * Converts a string to a {@link ConfigurationImportParameters} from form data of incoming requests.
 * Is automatically applied without additional configuration.
 *
 * @author Daniel Preciado-Marquez
 */
@Component
public class StringToConfigurationImportParametersConverter extends CinnamonStringConverter<ConfigurationImportParameters> {
	public StringToConfigurationImportParametersConverter(final SerializationConfig serializationConfig) {
		super(ConfigurationImportParameters.class, serializationConfig);
	}
}
