package de.kiaim.cinnamon.platform.converter;


import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.configuration.Job;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converter a {@link Job} to a string and back when persisting the job in the database.
 * Has to be explicitly triggered with {@link jakarta.persistence.Convert}.
 *
 * @author Daniel Preciado-Marquez
 */
@Converter
public class JobAttributeConverter implements AttributeConverter<Job, String> {

	private final CinnamonConfiguration cinnamonConfiguration;

	public JobAttributeConverter(final CinnamonConfiguration cinnamonConfiguration) {
		this.cinnamonConfiguration = cinnamonConfiguration;
	}

	@Override
	public String convertToDatabaseColumn(final Job attribute) {
		return attribute.getName();
	}

	@Override
	public Job convertToEntityAttribute(final String dbData) {
		return cinnamonConfiguration.getSteps().get(dbData);
	}
}
