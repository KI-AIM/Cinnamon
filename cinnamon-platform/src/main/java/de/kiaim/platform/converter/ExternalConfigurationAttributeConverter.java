package de.kiaim.platform.converter;

import de.kiaim.platform.model.configuration.ExternalConfiguration;
import de.kiaim.platform.model.configuration.CinnamonConfiguration;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converter a {@link ExternalConfiguration} to a string and back when persisting the configuration in the database.
 * Has to be explicitly triggered with {@link jakarta.persistence.Convert}.
 *
 * @author Daniel Preciado-Marquez
 */
@Converter
public class ExternalConfigurationAttributeConverter implements AttributeConverter<ExternalConfiguration, String> {

	private final CinnamonConfiguration cinnamonConfiguration;

	public ExternalConfigurationAttributeConverter(final CinnamonConfiguration cinnamonConfiguration) {
		this.cinnamonConfiguration = cinnamonConfiguration;
	}

	@Override public String convertToDatabaseColumn(final ExternalConfiguration attribute) {
		return attribute.getConfigurationName();
	}

	@Override public ExternalConfiguration convertToEntityAttribute(final String dbData) {
		return cinnamonConfiguration.getExternalConfiguration().get(dbData);
	}
}
