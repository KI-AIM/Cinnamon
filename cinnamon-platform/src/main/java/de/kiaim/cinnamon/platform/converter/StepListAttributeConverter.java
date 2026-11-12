package de.kiaim.cinnamon.platform.converter;

import de.kiaim.cinnamon.platform.model.configuration.Job;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Converter
public class StepListAttributeConverter implements AttributeConverter<List<Job>, String> {

	private static final String SEPARATOR = ",";

	private final CinnamonConfiguration cinnamonConfiguration;

	public StepListAttributeConverter(final CinnamonConfiguration cinnamonConfiguration) {
		this.cinnamonConfiguration = cinnamonConfiguration;
	}


	@Override
	public @Nullable String convertToDatabaseColumn(final @Nullable List<Job> attribute) {
		if (attribute == null || attribute.isEmpty()) {
			return null;
		}

		return attribute.stream().map(Job::getName).collect(Collectors.joining(SEPARATOR));
	}

	@Override
	public List<@Nullable Job> convertToEntityAttribute(final @Nullable String dbData) {
		if (dbData == null || dbData.isEmpty()) {
			return List.of();
		}

		return Arrays.stream(dbData.split(SEPARATOR))
		             .map(name -> cinnamonConfiguration.getSteps().get(name))
		             .collect(Collectors.toList());
	}
}
