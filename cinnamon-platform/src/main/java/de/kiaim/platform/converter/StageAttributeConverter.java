package de.kiaim.platform.converter;

import de.kiaim.platform.model.configuration.KiAimConfiguration;
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

	private final KiAimConfiguration kiAimConfiguration;

	public StageAttributeConverter(final KiAimConfiguration kiAimConfiguration) {
		this.kiAimConfiguration = kiAimConfiguration;
	}

	@Override public String convertToDatabaseColumn(final Stage attribute) {
		return attribute.getStageName();
	}

	@Override public Stage convertToEntityAttribute(final String dbData) {
		return kiAimConfiguration.getStages().get(dbData);
	}
}
