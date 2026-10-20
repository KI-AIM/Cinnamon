package de.kiaim.cinnamon.model.serialization;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.node.ArrayNode;
import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.model.data.Data;
import de.kiaim.cinnamon.model.data.DataBuilder;
import de.kiaim.cinnamon.model.data.DataRow;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.model.exception.ConfigurationFormatException;
import de.kiaim.cinnamon.model.helper.DataTransformationHelper;
import de.kiaim.cinnamon.model.serialization.exception.DataFormatException;
import de.kiaim.cinnamon.model.serialization.exception.InvalidDatatypeJsonException;
import de.kiaim.cinnamon.model.serialization.mapper.CinnamonJsonMapper;

import java.util.ArrayList;
import java.util.List;

public class DataSetDeserializer extends ValueDeserializer<DataSet> {

	ObjectMapper mapper = CinnamonJsonMapper.jsonMapper();

	DataTransformationHelper dataTransformationHelper = new DataTransformationHelper();

	@Override
	public DataSet deserialize(JsonParser p, DeserializationContext ctxt) {
		final JsonNode jsonNode = p.objectReadContext().readTree(p);

		final DataConfiguration dataConfiguration = mapper.treeToValue(jsonNode.get("dataConfiguration"),
		                                                               DataConfiguration.class);

		final List<DataRow> dataRows = new ArrayList<>();
		final var dataJson = (ArrayNode) jsonNode.get("data");

		for (final var rowJson : dataJson) {
			final List<Data> data = new ArrayList<>();

			final var rowArray = (ArrayNode) rowJson;

			for (int colIndex = 0 ; colIndex < rowArray.size() ; colIndex++) {
				var dataType = dataConfiguration.getConfigurations().get(colIndex).getType();

				final DataBuilder builder;
				try {
					builder = dataTransformationHelper.getDataBuilderOrThrow(dataType);
				} catch (ConfigurationFormatException e) {
					throw new InvalidDatatypeJsonException("Could not get DataBuilder for type '" + dataType.name() + "'!",
					                                       p.currentLocation(), e);
				}

				var valJson = rowArray.get(colIndex);

				Data value;
				if (valJson.isNull()) {
					value = builder.buildNull();
				} else {
					var stringValue = valJson.asString();
					try {
						value = builder.setValue(stringValue, new ArrayList<>()).build();
					} catch (Exception e) {
						throw new DataFormatException("Could not parse value", e);
					}
				}

				data.add(value);
			}

			dataRows.add(new DataRow(data));
		}

		return new DataSet(dataRows, dataConfiguration);
	}
}
