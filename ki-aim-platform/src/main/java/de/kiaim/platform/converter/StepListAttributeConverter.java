package de.kiaim.platform.converter;

import de.kiaim.platform.model.enumeration.Step;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Converter
public class StepListAttributeConverter implements AttributeConverter<List<Step>, String> {

	private static final String SEPARATOR = ",";

	@Override
	public String convertToDatabaseColumn(final List<Step> attribute) {
		if (attribute == null || attribute.isEmpty()) {
			return null;
		}

		return attribute.stream().map(Enum::name).collect(Collectors.joining(SEPARATOR));
	}

	@Override
	public List<Step> convertToEntityAttribute(final String dbData) {
		if (dbData == null || dbData.isEmpty()) {
			return List.of();
		}

		return Arrays.stream(dbData.split(SEPARATOR)).map(Step::valueOf).collect(Collectors.toList());
	}
}
