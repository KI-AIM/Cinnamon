package de.kiaim.cinnamon.model.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import de.kiaim.cinnamon.model.configuration.data.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.Configuration;
import de.kiaim.cinnamon.model.configuration.data.RangeConfiguration;
import de.kiaim.cinnamon.model.data.Data;
import de.kiaim.cinnamon.model.data.DataBuilder;
import de.kiaim.cinnamon.model.data.DateData;
import de.kiaim.cinnamon.model.data.DateTimeData;
import de.kiaim.cinnamon.model.enumeration.DataScale;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.model.exception.ConfigurationFormatException;
import de.kiaim.cinnamon.model.exception.DataBuildingException;
import de.kiaim.cinnamon.model.helper.DataTransformationHelper;
import de.kiaim.cinnamon.model.serialization.exception.DataBuildingJsonException;
import de.kiaim.cinnamon.model.serialization.exception.InvalidDatatypeJsonException;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ColumnConfigurationDeserializer extends JsonDeserializer<ColumnConfiguration> {

	private final DataTransformationHelper dataTransformationHelper = new DataTransformationHelper();

	@Override
	public ColumnConfiguration deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		final JsonNode jsonNode = p.getCodec().readTree(p);

		final Integer index = jsonNode.has("index")
		                      ? jsonNode.get("index").asInt()
		                      : null;
		final String columnName = jsonNode.has("name")
		                          ? jsonNode.get("name").asText()
		                          : null;
		final DataType type = jsonNode.has("type")
		                      ? p.getCodec().readValue(jsonNode.get("type").traverse(), DataType.class)
		                      : null;
		final DataScale scale = jsonNode.has("scale")
		                        ? p.getCodec().readValue(jsonNode.get("scale").traverse(), DataScale.class)
		                        : null;

		final ArrayNode configurationNodes = (ArrayNode) jsonNode.get("configurations");
		final List<Configuration> configurations = convertConfigurationArrayValue(configurationNodes, type, p);

		return new ColumnConfiguration(index, columnName, type, scale, configurations);
	}

	/**
	 * Converts a value from a JsonNode into a DataObject.
	 * @param node The JSON node to be converted.
	 * @param targetType The data type the node should be converted to.
	 * @return The Data object.
	 * @throws ConfigurationFormatException if the data type is invalid.
	 * @throws DataBuildingException if the node could not be transformed.
	 */
	private Data convertRangeValue(final JsonNode node, final DataType targetType)
			throws ConfigurationFormatException, DataBuildingException {
		final Data result;

		// Postgres stores the JSON dates as arrays, so they need a special conversion
		if (node.isArray() && targetType == DataType.DATE) {
			result = arrayNodeToDate((ArrayNode) node);
		} else if (node.isArray() && targetType == DataType.DATE_TIME) {
			result = arrayNodeToDateTime((ArrayNode) node);
		} else {
			final DataBuilder builder = dataTransformationHelper.getDataBuilderOrThrow(targetType);
			result = builder.setValue(node.asText(), new ArrayList<>()).build();
		}

		return result;
	}

	/**
	 * Converts a JSON ArrayNode into an DateData.
	 * @param node The JSON ArrayNode to be converted.
	 * @return The DateData.
	 */
	private DateData arrayNodeToDate(ArrayNode node) {
		Integer[] numbers = readArrayNode(node);
		return new DateData(LocalDate.of(numbers[0], numbers[1], numbers[2]));
	}

	/**
	 * Converts a JSON ArrayNode into an DateTimeData.
	 * @param node The JSON ArrayNode to be converted.
	 * @return The DateTimeData.
	 */
	private DateTimeData arrayNodeToDateTime(ArrayNode node) {
		Integer[] numbers = readArrayNode(node);
		return new DateTimeData(
				LocalDateTime.of(numbers[0], numbers[1], numbers[2], numbers[3], numbers[4], numbers[5], numbers[6]));
	}

	/**
	 * Creates an integer array of seven integers and fills it with the content of an JSON ArrayNode;
	 * @param arrayNode The JSON array node for filling the array.
	 * @return The integer array with seven elements.
	 */
	private Integer[] readArrayNode(final ArrayNode arrayNode) {
		final Integer[] numbers = new Integer[7];
		Arrays.fill(numbers, 0);

		int index = 0;
		for (final JsonNode node : arrayNode) {
			numbers[index] = node.asInt();
			index += 1;
		}

		return numbers;
	}

	private List<Configuration> convertConfigurationArrayValue(@Nullable final JsonNode configurationArrayNode,
	                                                           @Nullable final DataType type,
	                                                           final JsonParser jsonParser)
			throws IOException {
		final List<Configuration> configurations = new ArrayList<>();

		if (configurationArrayNode == null) {
			return configurations;
		}

		final ArrayNode configurationNodes = (ArrayNode) configurationArrayNode;

		for (JsonNode configurationNode : configurationNodes) {
			final String configurationName = configurationNode.get("name").asText();
			if (configurationName.equals("RangeConfiguration")) {
				if (type == null) {
					configurations.add(new RangeConfiguration(null, null));
				} else {
					try {
						final Data minValue = convertRangeValue(configurationNode.get("minValue"), type);
						final Data maxValue = convertRangeValue(configurationNode.get("maxValue"), type);
						configurations.add(new RangeConfiguration(minValue, maxValue));
					} catch (ConfigurationFormatException e) {
						throw new InvalidDatatypeJsonException(
								"Could not convert 'minValue' and 'maxValue' of RangeConfiguration because the column configuration contains an invalid data type!",
								configurationNode.traverse().currentLocation(), e);
					} catch (DataBuildingException e) {
						throw new DataBuildingJsonException(
								"Could not convert 'minValue' and 'maxValue' of RangeConfiguration because of an invalid format!",
								configurationNode.traverse().currentLocation(), e);
					}
				}
			} else {
				configurations.add(jsonParser.getCodec().readValue(configurationNode.traverse(), Configuration.class));
			}
		}

		return configurations;
	}
}
