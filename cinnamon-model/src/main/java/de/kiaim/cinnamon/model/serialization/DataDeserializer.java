package de.kiaim.cinnamon.model.serialization;

import tools.jackson.core.JsonParser;
import tools.jackson.core.TokenStreamContext;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import de.kiaim.cinnamon.model.data.Data;
import de.kiaim.cinnamon.model.data.DataBuilder;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.model.exception.ConfigurationFormatException;
import de.kiaim.cinnamon.model.helper.DataTransformationHelper;
import de.kiaim.cinnamon.model.serialization.exception.DataFormatException;
import de.kiaim.cinnamon.model.serialization.exception.InvalidDatatypeJsonException;
import de.kiaim.cinnamon.model.serialization.exception.NoConfigurationInContextException;

import java.util.ArrayList;
import java.util.List;

public class DataDeserializer extends ValueDeserializer<Data> {

	DataTransformationHelper dataTransformationHelper = new DataTransformationHelper();

	@Override
	public Data deserialize(JsonParser p, DeserializationContext ctxt) {
		DataType dataType = getDataTypeFromContext(p);

		final DataBuilder builder;
		try {
			builder = dataTransformationHelper.getDataBuilderOrThrow(dataType);
		} catch (ConfigurationFormatException e) {
			throw new InvalidDatatypeJsonException("Could not get DataBuilder for type '" + dataType.name() + "'!",
			                                       p.currentLocation(), e);
		}

		final Data value;
		try {
			final JsonNode jsonNode = p.objectReadContext().readTree(p);
			value = builder.setValue(jsonNode.toString(), new ArrayList<>()).build();
		} catch (Exception e) {
			throw new DataFormatException("Could not parse value", e);
		}

		return value;
	}

	private static DataType getDataTypeFromContext(JsonParser p) {
		final TokenStreamContext parent = p.streamReadContext().getParent();

		DataType dataType = null;
		if (parent.currentValue() instanceof List) {
			final TokenStreamContext grandParent = parent.getParent();
			if (grandParent.currentValue() instanceof ColumnConfiguration) {
				dataType = ((ColumnConfiguration) grandParent.currentValue()).getType();
			}
		}

		if (dataType == null) {
			throw new NoConfigurationInContextException("No configuration found in deserialization context!");
		}

		return dataType;
	}
}
