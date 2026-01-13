package de.kiaim.cinnamon.platform.processor;

import de.kiaim.cinnamon.model.configuration.data.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.Configuration;
import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.data.*;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.model.exception.DataBuildingException;
import de.kiaim.cinnamon.model.helper.DataTransformationHelper;
import de.kiaim.cinnamon.platform.exception.BadDatasetException;
import de.kiaim.cinnamon.platform.model.DataRowTransformationError;
import de.kiaim.cinnamon.platform.model.DataTransformationError;
import de.kiaim.cinnamon.platform.model.Pair;
import de.kiaim.cinnamon.platform.model.dto.DataConfigurationEstimation;
import de.kiaim.cinnamon.platform.model.enumeration.DatatypeEstimationAlgorithm;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;
import java.util.stream.Collectors;

@Getter
public abstract class CommonDataProcessor implements DataProcessor {

	/**
	 * List of potential attribute names in the header used for estimating if a file contains a header row.
	 */
	@Value("${cinnamon.estimation.attributes}")
	protected List<String> suggestedAttributes;

	/**
	 * Number of values in a row that must fulfill the criteria of a header value for the row to be considered a header.
	 * Used for estimating if a file contains a header row.
	 */
	@Value("${cinnamon.estimation.min-matches}")
	protected int minMatches;

	/**
	 * Max number of samples used for the column configuration estimation.
	 */
	@Value("${cinnamon.estimation.sample-size}")
	public int maxSampleSize;

    @Autowired
    DataTransformationHelper dataTransformationHelper;

	/**
	 * Estimates if the given first row is a header row by detecting attribute names and data type differences.
	 *
	 * @param first  The first row of the data set.
	 * @param second The second row of the data set.
	 * @return Estimation if the first row is a header row.
	 */
	protected boolean estimateHasHeader(final List<String> first, final List<String> second) {
		// Check if the first row contains many attribute names
		int matchingAttributeNames = 0;
		for (final var head : first) {
			for (final var suggestedAttribute : suggestedAttributes) {
				if (head.equalsIgnoreCase(suggestedAttribute)) {
					matchingAttributeNames++;

					if (matchingAttributeNames >= minMatches) {
						return true;
					}

					break;
				}
			}
		}

		// Check if the data types between the first and second row differ
		var typeMathes = 0;
		for (int i = 0; i < first.size(); i++) {
			final var head = first.get(i);
			final var sec = second.get(i);

			final var headType = estimateColumnConfigurationFromSample(head).getType();
			final var secType = estimateColumnConfigurationFromSample(sec).getType();

			if (headType == DataType.STRING && secType != DataType.UNDEFINED && headType != secType) {
				typeMathes++;

				if (typeMathes>= minMatches) {
					return true;
				}
			}
		}

		return false;
	}

    /**
     * Transforms a row into a DataRow and appends it to the given list of data rows.
     * Upon transformation, each value is validated.
     * If a fault has been detected,
     * the error will be added to the DataRowTransformationError list with the
     * corresponding raw row string and the row index.
     * If an error was detected for any value in a row, the complete row will
     * not be added to the list of DataRows.
     *
     * @param row Input: Row to transform
     * @param rowIndex Input index of the row
     * @param configuration Configuration of the data.
     * @param dataRows List containing valid rows
     * @param errors List of errors
     * @throws BadDatasetException If the row has too few or too many values.
     */
    public void transformRow(List<String> row, int rowIndex, DataConfiguration configuration,
                             List<DataRow> dataRows, List<DataRowTransformationError> errors) throws BadDatasetException {
		if (row.size() < configuration.getConfigurations().size()) {
			throw new BadDatasetException(BadDatasetException.ROW_TOO_FEW_VALUES,
			                              "The row " + (rowIndex + 1) + " contains too few values: expected " +
			                              configuration.getConfigurations().size() + ", but got " + row.size() + "!");
		}
		if (row.size() > configuration.getConfigurations().size()) {
			throw new BadDatasetException(BadDatasetException.ROW_TOO_MANY_VALUES,
			                              "The row " + (rowIndex + 1) + " contains too many values: expected " +
			                              configuration.getConfigurations().size() + ", but got " + row.size() + "!");
		}

        // Process every row
        boolean errorInRow = false;

        List<Data> transformedCol = new ArrayList<>();

        // Transformation error that was thrown inside the Data builders
        DataRowTransformationError newError = new DataRowTransformationError(rowIndex);

        for (int colIndex = 0; colIndex < row.size(); colIndex++) {
            // Process every column value in a row
            String col = row.get(colIndex);

            List<ColumnConfiguration> matchingColumnConfigurations =
                    getColumnConfigurationForIndex(configuration.getConfigurations(), colIndex);

            // Every index should appear exactly once
            assert !matchingColumnConfigurations.isEmpty();
            ColumnConfiguration columnConfiguration = matchingColumnConfigurations.get(0);

            try {
                Data transformedData = this.dataTransformationHelper.transformData(col, columnConfiguration);
                transformedCol.add(transformedData);
            } catch (DataBuildingException e) {
                //Resolve to error type, to easier parse information to frontend
                newError.addError(new DataTransformationError(colIndex, e.getTransformationErrorType(), col));

                //Set Flag so error will be added
                errorInRow = true;

                // Remove faulty value from dataset
                transformedCol.add(this.dataTransformationHelper.transformNullValue(columnConfiguration));
            }
        }

        dataRows.add(new DataRow(transformedCol));

        if (errorInRow) {
            //Add error to errorList
            errors.add(newError);
        }
    }

    /**
     * Estimates the DataConfiguration for multiple attributes.
     * The estimation is performed for each sample of each attribute individually.
     * Afterward the different results are counted.
     * The datatype for each attribute is chosen based on the given DataTypeEstimationAlgorithm.
     * Samples that contain the estimated datatype are then used for determining the configurations.
     *
     * @param samples Column major list containing samples for each attribute.
     * @param algorithm Algorithm how to select the datatype of a column.
     * @param numberColumns The number of columns in the dataset. Used if the given rows are empty.
     * @param columnNames The names of the columns.
     * @return The estimated DataConfiguration.
     */
    public DataConfigurationEstimation estimateDataConfiguration(
            final List<List<String>> samples,
            final DatatypeEstimationAlgorithm algorithm,
            final int numberColumns,
            final List<String> columnNames
    ) {
        final List<ColumnConfiguration> estimatedColumnConfigurations = new ArrayList<>(numberColumns);
        final float[] confidence = new float[numberColumns];

        for (int sampleIndex = 0; sampleIndex < samples.size(); sampleIndex++) {
            final var attributeSample = samples.get(sampleIndex);

            if (attributeSample.isEmpty()) {
                estimatedColumnConfigurations.add(new ColumnConfiguration());
            } else {
                final var estimation = estimateColumnConfiguration(attributeSample, algorithm);
                estimatedColumnConfigurations.add(estimation.element0());
                confidence[sampleIndex] = estimation.element1();
            }
        }

        final DataConfiguration dataConfiguration = buildDataConfiguration(estimatedColumnConfigurations, columnNames);
        return new DataConfigurationEstimation(dataConfiguration, confidence);
    }

    /**
     * Normalizes column names by replacing spaces with underscores.
     * @param columnNames The column names to be normalized.
     * @return The normalize column names.
     */
    public List<String> normalizeColumnNames(final String[] columnNames) {
        return Arrays.stream(columnNames).map(columnName -> columnName.replace(" ", "_")).toList();
    }

    /**
     * Extracts samples for each attribute from the given rows.
     * Outer list of the result contains an entry for each attribute. Inner lists contain at maximum {@link #maxSampleSize} values.
     *
     * @param rows          Rows of the dataset without header.
     * @param numberColumns Number of attributes in the dataset.
     * @return The samples.
     */
    protected List<List<String>> getAttributeSamples(final Iterator<? extends Iterable<String>> rows, final int numberColumns) {
        // Prepare result
        int finishedCount = 0;
        final List<List<String>> samples = new ArrayList<>();
        for (int i = 0; i < numberColumns; i++) {
            samples.add(new ArrayList<>());
        }

        // Iterate over rows until all targets are reached
        while (rows.hasNext() && finishedCount < numberColumns) {
            final var row = rows.next();
            final var rowIterator = row.iterator();

            int currentColumn = 0;
            while (rowIterator.hasNext() && currentColumn < numberColumns) {
                final var value = rowIterator.next();

                // Only look for more values if more samples are needed
                if (samples.get(currentColumn).size() < maxSampleSize) {
                    // Add value to samples if not empty
                    if (!dataTransformationHelper.isValueEmpty(value)) {
                        samples.get(currentColumn).add(value);

                        // Increase counter for finished columns if target amount is reached
                        if (samples.get(currentColumn).size() == maxSampleSize) {
                            finishedCount++;
                        }
                    }
                }

                currentColumn++;
            }

        }

        return samples;
    }

    private List<ColumnConfiguration> getColumnConfigurationForIndex(
            List<ColumnConfiguration> configurations,
            int index
    ) {
        return configurations
                .stream()
                .filter(it -> it.getIndex() == index)
                .toList();
    }

    /**
     * Estimates the data type and configurations for an attribute.
     * The estimation is performed for each sample individually.
     * Afterward the different results are counted.
     * The datatype of the attribute is chosen based on the given DataTypeEstimationAlgorithms.
     * Samples that contain the estimated datatype are then used for determining the configurations.
     *
     * @param attributeSamples List containing samples of a single attribute.
     * @param algorithm Algorithm how to select the datatype of a column.
     * @return List of estimated ColumnConfigurations.
     */
    private Pair<ColumnConfiguration, Float> estimateColumnConfiguration(
            final List<String> attributeSamples,
            final DatatypeEstimationAlgorithm algorithm
    ) {
        // Estimate the column configuration for all samples
        final List<ColumnConfiguration> columnConfigurationForSamples = new ArrayList<>();
        for (final String attributeSample : attributeSamples) {
            columnConfigurationForSamples.add(estimateColumnConfigurationFromSample(attributeSample));
        }

        // Get the estimated data type based on the given algorithm
        final List<DataType> dataTypes = extractDataTypes(columnConfigurationForSamples);
        final Map<DataType, Integer> countedEstimatedDatatypes = countEstimatedDatatypesForColumn(dataTypes);
        final var estimatedDataType = switch (algorithm) {
            case MOST_ESTIMATED -> getMostEstimatedDatatypeFromCountMap(countedEstimatedDatatypes);
            case MOST_GENERAL -> getMostGeneralDatatypeFromCountMap(countedEstimatedDatatypes);
        };

        final var configs = getMostEstimatedConfiguration(estimatedDataType, columnConfigurationForSamples);
        var columnConfiguration = new ColumnConfiguration();
        columnConfiguration.setType(estimatedDataType);
        columnConfiguration.setConfigurations(configs);

        // Calculate the number of samples that match the estimated configuration
        final float confidence = calculateConfidence(attributeSamples, columnConfiguration);

        return new Pair<>(columnConfiguration, confidence);
    }

    /**
     * Tries to estimate a column configuration for the given sample.
     * The first valid transformation determines the datatype estimation and the configurations.
     *
     * @param sample A single sample value.
     * @return The estimated column configuration.
     */
    private ColumnConfiguration estimateColumnConfigurationFromSample(final String sample) {
        List<Pair<DataType, DataBuilder>> processingOrder = getProcessingOrder();
        var columnConfiguration = new ColumnConfiguration();

        for (final Pair<DataType, DataBuilder> processor : processingOrder) {
            final var estimationResult = processor.element1().estimateColumnConfiguration(sample);

            if (estimationResult.getType() != DataType.UNDEFINED) {
                columnConfiguration = estimationResult;
                break;
            }
        }

        return columnConfiguration;
    }

    /**
     * Returns a list the most estimated configurations for the given column configurations.
     * ColumnConfigurations with other data types than the given one will be ignored.
     * The returned list contains each appearing class once.
     * Uses the equals method to compare instances.
     *
     * @param estimatedDataType The estimated data type for the column.
     * @param estimationResult  All estimated column configurations.
     * @return The configurations.
     */
    private List<Configuration> getMostEstimatedConfiguration(
            final DataType estimatedDataType,
            final List<ColumnConfiguration> estimationResult
    ) {
        final Map<Class<?>, Map<Configuration, Integer>> resultMap = new HashMap<>();

        // Iterate over all estimated configurations
        for (final var columnEstimationResult : estimationResult) {
            // If the data type is different, the configurations can be ignored
            if (columnEstimationResult.getType() != estimatedDataType) {
                continue;
            }

            for (final var config : columnEstimationResult.getConfigurations()) {
                // Create a new entry in the map if the class appears the first time
                if (!resultMap.containsKey(config.getClass())) {
                    resultMap.put(config.getClass(), new HashMap<>());
                }

                var map = resultMap.get(config.getClass());
                if (map.isEmpty()) {
                    map.put(config, 1);
                } else {
                    var isPresent = false;
                    // Compare current instance with all other instances of the same class
                    for (final var existingConfig : map.entrySet()) {
                        if (existingConfig.getKey().equals(config)) {
                            // An instances equal to the current one is present, so increase counter
                            existingConfig.setValue(existingConfig.getValue() + 1);
                            isPresent = true;
                            break;
                        }
                    }
                    if (!isPresent) {
                        // Instance is unique under the current ones
                        map.put(config, 1);
                    }
                }
            }

        }

        return getMostEstimatedInstances(resultMap);
    }

    /**
     * Searches for each class, the most appeared instance.
     * @param resultMap Map containing for each class the number of equal appearances for each instance.
     * @return The instances.
     */
    private static List<Configuration> getMostEstimatedInstances(Map<Class<?>, Map<Configuration, Integer>> resultMap) {
        final List<Configuration> result = new ArrayList<>();
        for (final var configs : resultMap.values()) {
            Configuration mostConfig = null;
            Integer mostConfigCount = 0;

            for (final var entry : configs.entrySet()) {
                if (entry.getValue() > mostConfigCount) {
                    mostConfig = entry.getKey();
                    mostConfigCount = entry.getValue();
                }
            }

            if (mostConfig != null) {
                result.add(mostConfig);
            }
        }
        return result;
    }

    /**
     * Counts the number of DataType occurrences for a single column.
     * @param column List of datatype estimations for a column
     * @return Count Map Map<DataType, Integer>
     */
    private Map<DataType, Integer> countEstimatedDatatypesForColumn(List<DataType> column) {
        Map<DataType, Integer> resultMap = initializeDatatypeCountMap();

        for (DataType columnEstimation : column) {
            resultMap.compute(columnEstimation, (key, value) -> value == null ? 1 : value + 1);
        }

        return resultMap;
    }

    /**
     * Initializes a count map with the DataTypes and the value 0
     * @return Count Map Map<DataType, Integer>
     */
    private Map<DataType, Integer> initializeDatatypeCountMap() {
        Map<DataType, Integer> resultMap = new HashMap<>();

        resultMap.put(DataType.BOOLEAN, 0);
        resultMap.put(DataType.DATE, 0);
        resultMap.put(DataType.DATE_TIME, 0);
        resultMap.put(DataType.DECIMAL, 0);
        resultMap.put(DataType.INTEGER, 0);
        resultMap.put(DataType.STRING, 0);
        resultMap.put(DataType.UNDEFINED, 0);

        return resultMap;
    }

    /**
     * Filters the count maps and returns the DataType for the
     * largest value. If two values are identical the first
     * result in the map is returned
     * @param countMap The filled out count map Map<DataType, Integer>
     * @return DataType
     */
    private DataType getMostEstimatedDatatypeFromCountMap(Map<DataType, Integer> countMap) {
        return Collections.max(countMap.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    /**
     * Filters the count maps and returns the DataType that is the most general.
     * @param countMap The filled out count map Map<DataType, Integer>
     * @return DataType
     */
    private DataType getMostGeneralDatatypeFromCountMap(final Map<DataType, Integer> countMap) {
        final var processingOrder = getProcessingOrder();
        for (int i = processingOrder.size() - 1; i >= 0; i--) {
            final DataType dataType = processingOrder.get(i).element0();
            if (countMap.get(dataType) > 0) {
                return dataType;
            }
        }

        return processingOrder.get(processingOrder.size() - 1).element0();
    }

    /**
     * Returns a list of data types for a given list of column configurations.
     *
     * @param list List of ColumnConfiguration.
     * @return The data types.
     */
    private List<DataType> extractDataTypes(List<ColumnConfiguration> list) {
        return list.stream().map(ColumnConfiguration::getType).collect(Collectors.toList());
    }

    /**
     * Function that returns a list of DataTypes with the
     * corresponding DataBuilder object.
     * It is used to receive a predefined processing order
     * @return List of Pairs with DataTypes and DataBuilder objects
     */
    private List<Pair<DataType, DataBuilder>> getProcessingOrder() {
        return Arrays.asList(
                new Pair<>(DataType.INTEGER, new IntegerData.IntegerDataBuilder()),
                new Pair<>(DataType.DECIMAL, new DecimalData.DecimalDataBuilder()),
                new Pair<>(DataType.BOOLEAN, new BooleanData.BooleanDataBuilder()),
                new Pair<>(DataType.DATE, new DateData.DateDataBuilder()),
                new Pair<>(DataType.DATE_TIME, new DateTimeData.DateTimeDataBuilder()),
                new Pair<>(DataType.STRING, new StringData.StringDataBuilder())
        );
    }

    /**
     * Builds a new DataConfiguration for a list of ColumnConfigurations.
     * The column configurations only contain the estimated data type and configurations.
     * All other attributes will be set.
     *
     * @param columnConfigurations Estimated column configurations.
     * @param columnNames list containing the column names
     * @return new DataConfiguration object
     */
    private DataConfiguration buildDataConfiguration(
            final List<ColumnConfiguration> columnConfigurations,
            List<String> columnNames
    ) {
        DataConfiguration resultingConfiguration = new DataConfiguration();

        for (int i = 0; i < columnConfigurations.size(); i++) {
            var columnConfiguration = columnConfigurations.get(i);
            columnConfiguration.setIndex(i);
            columnConfiguration.setName(columnNames.get(i));
            columnConfiguration.setScale(columnConfiguration.getType().getDefaultScale());
        }
        resultingConfiguration.setConfigurations(columnConfigurations);

        return resultingConfiguration;
    }

    /**
     * Calculates the percentage of samples that matches the given column configuration.
     *
     * @param attributeSamples             List of samples.
     * @param estimatedColumnConfiguration The column configuration.
     * @return Confidence between 0 and 1.
     */
    private float calculateConfidence(final List<String> attributeSamples,
                                      final ColumnConfiguration estimatedColumnConfiguration) {
        float correct = 0;
        for (final String attributeSample : attributeSamples) {
            try {
                this.dataTransformationHelper.transformData(attributeSample, estimatedColumnConfiguration);
                correct += 1;
            } catch (final DataBuildingException ignored) {
            }
        }

        return correct / attributeSamples.size();
    }

}
