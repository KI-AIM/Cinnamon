package de.kiaim.platform.converter;

import de.kiaim.platform.model.configuration.Job;
import de.kiaim.platform.model.configuration.KiAimConfiguration;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Converter
public class StepListAttributeConverter implements AttributeConverter<List<Job>, String> {

	private static final String SEPARATOR = ",";

	private final KiAimConfiguration kiAimConfiguration;

	public StepListAttributeConverter(final KiAimConfiguration kiAimConfiguration) {
		this.kiAimConfiguration = kiAimConfiguration;
	}


	@Override
	public String convertToDatabaseColumn(final List<Job> attribute) {
		if (attribute == null || attribute.isEmpty()) {
			return null;
		}

		return attribute.stream().map(Job::getName).collect(Collectors.joining(SEPARATOR));
	}

	@Override
	public List<Job> convertToEntityAttribute(final String dbData) {
		if (dbData == null || dbData.isEmpty()) {
			return List.of();
		}

		return Arrays.stream(dbData.split(SEPARATOR))
		             .map(name -> kiAimConfiguration.getSteps().get(name))
		             .collect(Collectors.toList());
	}
}
