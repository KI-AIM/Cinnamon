package de.kiaim.platform.processor;

import de.kiaim.model.configuration.data.ColumnConfiguration;
import de.kiaim.model.configuration.data.Configuration;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.data.*;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.model.exception.DataBuildingException;
import de.kiaim.model.helper.DataTransformationHelper;
import de.kiaim.platform.model.DataRowTransformationError;
import de.kiaim.platform.model.DataTransformationError;
import de.kiaim.platform.model.Pair;
import de.kiaim.platform.model.enumeration.DatatypeEstimationAlgorithm;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Getter
public abstract class CommonDataProcessor implements DataProcessor {

    @Autowired
    DataTransformationHelper dataTransformationHelper;

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
     */
    public void transformRow(List<String> row, int rowIndex, DataConfiguration configuration,
                             List<DataRow> dataRows, List<DataRowTransformationError> errors) {
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
     * Check whether every value of a column, stored in an array,
     * is valid and not empty
     * @param column Array to be processed
     * @return true, if column is complete; false otherwise
     */
    public boolean isColumnListComplete(String[] column) {
        boolean columnIsValid = true;
        for (String columnValue : column) {
            if (dataTransformationHelper.isValueEmpty(columnValue)) {
                columnIsValid = false;
            }
        }

        return columnIsValid;
    }

    /**
     * Processes every entry of a row and tries to estimate a column configuration for each value.
     * The first valid transformation determines the datatype estimation and the configurations.
     * @param row represented by an array with separated column values
     * @return List of ColumnConfigurations for every column
     */
    public List<ColumnConfiguration> estimateColumnConfigurationsFromRow(String[] row) {
        List<ColumnConfiguration> result = new ArrayList<>();

        for (String column : row) {
            List<Pair<DataType, DataBuilder>> processingOrder = getProcessingOrder();
            var columnConfiguration = new ColumnConfiguration();
            columnConfiguration.setType(DataType.UNDEFINED);

	        for (final Pair<DataType, DataBuilder> processor : processingOrder) {
		        final var estimationResult = processor.element1().estimateColumnConfiguration(column);

		        if (estimationResult.getType() != DataType.UNDEFINED) {
			        columnConfiguration = estimationResult;
			        break;
		        }
	        }

            result.add(columnConfiguration);
        }
        return result;

    }

    /**
     * Estimates the DataConfiguration for the given rows.
     * The estimation is performed for each row individually.
     * Afterward the different results are counted for every column.
     * The datatypes for each row are chosen based on the given DataTypeEstimationAlgorithms.
     * Values that contain the estimated datatype are then used for comparing the configurations.
     *
     * @param rows List containing the rows as String[]
     * @param algorithm Algorithm how to select the datatype of a column.
     * @param numberColumns The number of columns in the dataset. Used if the given rows are empty.
     * @param columnNames The names of the columns.
     * @return The estimated DataConfiguration.
     */
    public DataConfiguration estimateDataConfiguration(
            final List<String[]> rows,
            final DatatypeEstimationAlgorithm algorithm,
            final int numberColumns,
            final List<String> columnNames
    ) {
        final List<ColumnConfiguration> estimatedColumnConfigurations;
        if (rows.isEmpty()) {
            estimatedColumnConfigurations = getUndefinedColumnConfigurationList(numberColumns);
        } else {
            estimatedColumnConfigurations = estimateColumnConfigurationsForMultipleRows(rows, algorithm);
        }

        return buildDataConfiguration(estimatedColumnConfigurations, columnNames);
    }

    /**
     * Estimates the data type and configurations on multiple rows.
     * The estimation is performed for each row individually.
     * Afterward the different results are counted for every column.
     * The datatypes for each row are chosen based on the given DataTypeEstimationAlgorithms.
     * Values that contain the estimated datatype are then used for comparing the configurations.
     *
     * @param rows List containing the rows as String[]
     * @param algorithm Algorithm how to select the datatype of a column.
     * @return List of estimated ColumnConfigurations.
     */
    public List<ColumnConfiguration> estimateColumnConfigurationsForMultipleRows(
            List<String[]> rows,
            final DatatypeEstimationAlgorithm algorithm
    ) {
        List<List<ColumnConfiguration>> columnConfigurationForRows = new ArrayList<>();
        List<DataType> resultList = new ArrayList<>();

        for (String[] row : rows) {
            columnConfigurationForRows.add(estimateColumnConfigurationsFromRow(row));
        }

        List<Map<DataType, Integer>> countedEstimatedDatatypes = countEstimatedDatatypesForRows(columnConfigurationForRows);

        for (Map<DataType, Integer> countedMapForColumn : countedEstimatedDatatypes) {
            switch (algorithm) {
                case MOST_ESTIMATED -> resultList.add(getMostEstimatedDatatypeFromCountMap(countedMapForColumn));
                case MOST_GENERAL -> resultList.add(getMostGeneralDatatypeFromCountMap(countedMapForColumn));
            }
        }

        final List<ColumnConfiguration> result = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < resultList.size(); columnIndex++) {
            final var configs = getMostEstimatedConfiguration(columnIndex, resultList.get(columnIndex),
                                                              columnConfigurationForRows);
            var columnConfiguration = new ColumnConfiguration();
            columnConfiguration.setType(resultList.get(columnIndex));
            columnConfiguration.setConfigurations(configs);
            result.add(columnConfiguration);
        }

        return result;
    }

    /**
     * Returns a list the most estimated configurations in the given column configurations for the given column index.
     * ColumnConfigurations with other data types than the given one will be ignored.
     * The returned list contains each appearing class once.
     * Uses the equals method to compare instances.
     * 
     * @param columnIndex The column index of the column to analyze.
     * @param estimatedDataType The estimated data type for the column.
     * @param estimationResult All estimated column configurations.
     * @return The configurations.
     */
    private List<Configuration> getMostEstimatedConfiguration(
            final int columnIndex,
            final DataType estimatedDataType,
            final List<List<ColumnConfiguration>> estimationResult
    ) {
        Map<Class<?>, Map<Configuration, Integer>> resultMap = new HashMap<>();

        // Iterate over all rows
        for (final var rowEstimationResult : estimationResult) {
            // Get the estimation of the given column in the current row
            final var columnEstimationResult = rowEstimationResult.get(columnIndex);

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
     * Normalizes column names by replacing spaces with underscores.
     * @param columnNames The column names to be normalized.
     * @return The normalize column names.
     */
    public List<String> normalizeColumnNames(final String[] columnNames) {
        return Arrays.stream(columnNames).map(columnName -> columnName.replace(" ", "_")).toList();
    }

    /**
     * Performs the counting for the Collection of datatype
     * estimations for every row.
     * @param estimatedColumnConfigurations two-dimensional matrix; First dimension are the rows, second dimension the columns
     * @return A List of Count-Maps. For every Column a new Map entry is created
     */
    private List<Map<DataType, Integer>> countEstimatedDatatypesForRows(
            List<List<ColumnConfiguration>> estimatedColumnConfigurations) {
        List<List<DataType>> transposedList = extractDataTypes(estimatedColumnConfigurations);
        List<Map<DataType, Integer>> countedMapsForColumns = new ArrayList<>();

        for (List<DataType> columnResults : transposedList) {
            countedMapsForColumns.add(countEstimatedDatatypesForColumn(columnResults));
        }

        return countedMapsForColumns;
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
     * Returns a column major list of data types for a given list containing a list of column configuration.
     * @param list List of ColumnConfiguration.
     * @return The column major data types.
     */
    public static List<List<DataType>> extractDataTypes(List<List<ColumnConfiguration>> list) {
        final int N = list.stream().mapToInt(List::size).max().orElse(-1);
        List<Iterator<ColumnConfiguration>> iterList = list.stream().map(List::iterator).toList();
        return IntStream.range(0, N)
                        .mapToObj(n -> iterList.stream()
                                               .filter(Iterator::hasNext)
                                               .map(Iterator::next)
                                               .map(ColumnConfiguration::getType)
                                               .collect(Collectors.toList()))
                        .collect(Collectors.toList());
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
     * Creates a list of ColumnConfiguration for a given number of columns, where every type is set to UNDEFINED.
     * @param numberOfColumns depicting the length of the list
     * @return List with DataTypes
     */
    public List<ColumnConfiguration> getUndefinedColumnConfigurationList(int numberOfColumns) {
        List<ColumnConfiguration> result = new ArrayList<>();
        for (int i = 0; i < numberOfColumns; i++) {
            result.add(new ColumnConfiguration());
        }
        return result;
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
}
