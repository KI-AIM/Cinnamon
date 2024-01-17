package de.kiaim.platform.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import de.kiaim.platform.exception.BadDataTypeException;
import de.kiaim.platform.helper.DataTransformationHelper;
import de.kiaim.platform.model.data.Data;
import de.kiaim.platform.model.data.DataBuilder;
import de.kiaim.platform.model.data.DataType;
import de.kiaim.platform.model.data.configuration.ColumnConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DataDeserializer extends JsonDeserializer<Data> {

	// Autowired caused NullPointerExceptions in tests where the configuration gets saved as a JSON.
	DataTransformationHelper dataTransformationHelper = new DataTransformationHelper();

	@Override
	public Data deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		DataType dataType = getDataTypeFromContext(p);

		final DataBuilder builder;
		try {
			builder = dataTransformationHelper.getDataBuilderOrThrow(dataType);
		} catch (BadDataTypeException e) {
			throw new InvalidDatatypeJsonException("Could not get DataBuilder for type '" + dataType.name() + "'!",
			                                       p.currentLocation(), e);
		}

		final Data value;
		try {
			final JsonNode jsonNode = p.getCodec().readTree(p);
			value = builder.setValue(jsonNode.toString(), new ArrayList<>()).build();
		} catch (Exception e) {
			throw new DataFormatException("Could not parse value", e);
		}

		return value;
	}

	private static DataType getDataTypeFromContext(JsonParser p) throws NoConfigurationInContextException {
		final JsonStreamContext parent = p.getParsingContext().getParent();

		DataType dataType = null;
		if (parent.getCurrentValue() instanceof List) {
			final JsonStreamContext grandParent = parent.getParent();
			if (grandParent.getCurrentValue() instanceof ColumnConfiguration) {
				dataType = ((ColumnConfiguration) grandParent.getCurrentValue()).getType();
			}
		}

		if (dataType == null) {
			throw new NoConfigurationInContextException("No configuration found in deserialization context!");
		}

		return dataType;
	}
}
