package de.kiaim.platform.converter;

import de.kiaim.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.platform.model.configuration.Stage;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converter a {@link Stage} to a string and back when persisting the stage in the database.
 * Has to be explicitly triggered with {@link jakarta.persistence.Convert}.
 *
 * @author Daniel Preciado-Marquez
 */
@Converter
public class StageAttributeConverter implements AttributeConverter<Stage, String> {

	private final CinnamonConfiguration cinnamonConfiguration;

	public StageAttributeConverter(final CinnamonConfiguration cinnamonConfiguration) {
		this.cinnamonConfiguration = cinnamonConfiguration;
	}

	@Override public String convertToDatabaseColumn(final Stage attribute) {
		return attribute.getStageName();
	}

	@Override public Stage convertToEntityAttribute(final String dbData) {
		return cinnamonConfiguration.getStages().get(dbData);
	}
}
