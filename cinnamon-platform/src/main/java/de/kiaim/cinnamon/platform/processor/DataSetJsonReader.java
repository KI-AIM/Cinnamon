package de.kiaim.cinnamon.platform.processor;

import de.kiaim.cinnamon.model.configuration.data.attributes.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.Configuration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DateFormatConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DateTimeFormatConfiguration;
import de.kiaim.cinnamon.model.data.DataRow;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import de.kiaim.cinnamon.platform.exception.BadDatasetException;
import de.kiaim.cinnamon.platform.exception.InternalIOException;
import de.kiaim.cinnamon.platform.model.DataRowTransformationError;
import de.kiaim.cinnamon.platform.model.TransformationResult;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads data sets in the JSON format of {@link DataSet}, e.g. the results of the anonymization,
 * and validates the values against a data configuration like the file processors do.
 * <p>
 * Values violating the configuration are reported as transformation errors and replaced by null values,
 * instead of failing the whole data set.
 * Dates and date-times are ISO-8601 in the JSON format, independent of the format of the original file.
 * Therefore, the format configurations are ignored, while all other configurations (e.g. ranges and patterns)
 * are applied.
 * <p>
 * This is not a {@link DataProcessor}, as data sets in the JSON format cannot be uploaded.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class DataSetJsonReader {

	private final JsonMapper jsonMapper;
	private final DataRowTransformer dataRowTransformer;

	public DataSetJsonReader(final SerializationConfig serializationConfig,
	                         final DataRowTransformer dataRowTransformer) {
		this.jsonMapper = serializationConfig.jsonMapper();
		this.dataRowTransformer = dataRowTransformer;
	}

	/**
	 * Reads the data set and validates the values against the given configuration.
	 * The data configuration contained in the JSON is ignored.
	 *
	 * @param data          The data set in the JSON format.
	 * @param configuration The configuration the values are validated against.
	 * @return The data set with the given configuration and the transformation errors.
	 * @throws BadDatasetException If a row has too few or too many values.
	 * @throws InternalIOException If the data is not a data set in the JSON format.
	 */
	public TransformationResult read(final InputStream data, final DataConfiguration configuration)
			throws BadDatasetException, InternalIOException {
		final JsonNode rows;
		try {
			rows = jsonMapper.readTree(data).get("data");
		} catch (final JacksonException e) {
			throw new InternalIOException(InternalIOException.DATA_SET_READING, "Failed to parse the data set!", e);
		}
		if (rows == null || !rows.isArray()) {
			throw new InternalIOException(InternalIOException.DATA_SET_READING,
			                              "The data set does not contain an array 'data'!", null);
		}

		final DataConfiguration isoConfiguration = withoutFormats(configuration);
		final List<DataRow> dataRows = new ArrayList<>();
		final List<DataRowTransformationError> errors = new ArrayList<>();

		int rowIndex = 0;
		for (final JsonNode rowNode : rows) {
			final List<@Nullable String> row = new ArrayList<>(rowNode.size());
			for (final JsonNode valueNode : rowNode) {
				row.add(valueNode.isNull() ? null : valueNode.asString());
			}

			dataRowTransformer.transformRow(row, rowIndex, isoConfiguration, dataRows, errors);
			rowIndex++;
		}

		return new TransformationResult(new DataSet(dataRows, configuration), errors);
	}

	/**
	 * Copies the configuration without date and date-time formats, so the default ISO-8601 formats are used.
	 *
	 * @param configuration The data configuration.
	 * @return The copied configuration.
	 */
	private static DataConfiguration withoutFormats(final DataConfiguration configuration) {
		final DataConfiguration result = new DataConfiguration();
		for (final ColumnConfiguration columnConfiguration : configuration.getConfigurations()) {
			final List<Configuration> configurations = new ArrayList<>();
			for (final Configuration config : columnConfiguration.getConfigurations()) {
				if (!(config instanceof DateFormatConfiguration) && !(config instanceof DateTimeFormatConfiguration)) {
					configurations.add(config);
				}
			}

			result.addColumnConfiguration(new ColumnConfiguration(columnConfiguration.getIndex(),
			                                                      columnConfiguration.getName(),
			                                                      columnConfiguration.getType(),
			                                                      columnConfiguration.getScale(), configurations));
		}
		return result;
	}
}
