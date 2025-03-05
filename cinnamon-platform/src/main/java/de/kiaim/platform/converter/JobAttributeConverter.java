package de.kiaim.platform.converter;


import de.kiaim.platform.model.configuration.KiAimConfiguration;
import de.kiaim.platform.model.configuration.Job;
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

	private final KiAimConfiguration kiAimConfiguration;

	public JobAttributeConverter(final KiAimConfiguration kiAimConfiguration) {
		this.kiAimConfiguration = kiAimConfiguration;
	}

	@Override
	public String convertToDatabaseColumn(final Job attribute) {
		return attribute.getName();
	}

	@Override
	public Job convertToEntityAttribute(final String dbData) {
		return kiAimConfiguration.getSteps().get(dbData);
	}
}
