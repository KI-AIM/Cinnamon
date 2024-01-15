package de.kiaim.platform.json;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import de.kiaim.platform.helper.DataTransformationHelper;
import de.kiaim.platform.model.data.Data;
import de.kiaim.platform.model.data.DataBuilder;
import de.kiaim.platform.model.data.DataType;
import de.kiaim.platform.model.data.configuration.ColumnConfiguration;
import de.kiaim.platform.model.data.configuration.Configuration;
import de.kiaim.platform.model.data.configuration.DataScale;
import de.kiaim.platform.model.data.configuration.RangeConfiguration;
import lombok.SneakyThrows;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ColumnConfigurationDeserializer extends JsonDeserializer<ColumnConfiguration> {

	private final DataTransformationHelper dataTransformationHelper = new DataTransformationHelper();

	@SneakyThrows
	@Override
	public ColumnConfiguration deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		final JsonNode jsonNode = p.getCodec().readTree(p);
		final int index = jsonNode.get("index").asInt();
		final String columnName = jsonNode.get("name").asText();
		final DataType type = p.getCodec().readValue(jsonNode.get("type").traverse(), DataType.class);
		final DataScale scale = p.getCodec().readValue(jsonNode.get("scale").traverse(), DataScale.class);
		final List<Configuration> configurations = new ArrayList<>();
		final ArrayNode configurationNodes = (ArrayNode) jsonNode.get("configurations");
		for (JsonNode h : configurationNodes) {
			final String configurationName = h.get("name").asText();
			if (configurationName.equals("RangeConfiguration")) {
				final DataBuilder builder = dataTransformationHelper.getDataBuilder(type);
				final Data minValue = builder.setValue(h.get("minValue").asText(), new ArrayList<>()).build();
				final Data maxValue = builder.setValue(h.get("maxValue").asText(), new ArrayList<>()).build();
				configurations.add(new RangeConfiguration(minValue, maxValue));
			} else {
				configurations.add(p.getCodec().readValue(h.traverse(), Configuration.class));
			}
		}

		return new ColumnConfiguration(index, columnName, type, scale, configurations);
	}
}
