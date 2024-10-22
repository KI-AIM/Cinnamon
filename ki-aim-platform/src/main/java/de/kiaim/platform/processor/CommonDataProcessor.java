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
import org.apache.commons.lang3.tuple.ImmutablePair;
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
     * Processes every entry of a row and tries to convert it to one of the
     * available data types.
     * The first valid transformation determines the datatype estimation
     * @param row represented by an array with separated column values
     * @return List of DataTypes for every column
     */
    public List<ImmutablePair<DataType, List<Configuration>>> estimateDatatypesFromRow(String[] row) {
        List<ImmutablePair<DataType, List<Configuration>>> result = new ArrayList<>();

        for (String column : row) {
            List<Pair<DataType, DataBuilder>> processingOrder = getProcessingOrder();
            boolean foundMatchingDataType = false;

            for (int i = 0; i < processingOrder.size() && !foundMatchingDataType ; i++) {
                Pair<DataType, DataBuilder> processor = processingOrder.get(i);

                final var estimationResult = processor.element1().estimateColumnConfiguration(column);

                if (estimationResult.left) {
                    result.add(ImmutablePair.of(processor.element0(), estimationResult.right));
                    foundMatchingDataType = true;
                }
            }
            if (!foundMatchingDataType) {
                // If no processor was able to transform value it is UNDEFINED
                result.add(ImmutablePair.of(DataType.UNDEFINED, new ArrayList<>()));
            }
        }
        return result;

    }

    /**
     * Performs datatype estimation on multiple rows.
     * The datatype estimation is performed for each row
     * individually. Afterwards the different results are
     * counted for every column and the datatype
     * with the most results is used as the estimation
     * for the row.
     * @param rows List containing the rows as String[]
     * @param algorithm Algorithm how to select the datatype of a column.
     * @return List of datatype estimations
     */
    public List<ImmutablePair<DataType, List<Configuration>>> estimateDatatypesForMultipleRows(
            List<String[]> rows,
            final DatatypeEstimationAlgorithm algorithm
    ) {
        List<List<ImmutablePair<DataType, List<Configuration>>>> datatypesForRows = new ArrayList<>();
        List<DataType> resultList = new ArrayList<>();

        for (String[] row : rows) {
            datatypesForRows.add(estimateDatatypesFromRow(row));
        }

        List<Map<DataType, Integer>> countedEstimatedDatatypes = countEstimatedDatatypesForRows(datatypesForRows);

        for (Map<DataType, Integer> countedMapForColumn : countedEstimatedDatatypes) {
            switch (algorithm) {
                case MOST_ESTIMATED -> resultList.add(getMostEstimatedDatatypeFromCountMap(countedMapForColumn));
                case MOST_GENERAL -> resultList.add(getMostGeneralDatatypeFromCountMap(countedMapForColumn));
            }
        }

        final List<ImmutablePair<DataType, List<Configuration>>> result = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < resultList.size(); columnIndex++) {
            final var configs = getMostEstimatedConfiguration(columnIndex, resultList.get(columnIndex), datatypesForRows);
            result.add(ImmutablePair.of(resultList.get(columnIndex), configs));
        }

        return result;
    }

    private List<Configuration> getMostEstimatedConfiguration(
            final int columnIndex,
            final DataType estimatedDataType,
            final List<List<ImmutablePair<DataType, List<Configuration>>>> estimationResult
    ) {
        Map<Class<?>, Map<Configuration, Integer>> resultMap = new HashMap<>();

        // Iterate over all rows
        for (final List<ImmutablePair<DataType, List<Configuration>>> rowEstimationResult : estimationResult) {
            // Get the estimation of the given column in the current row
            final var columnEstimationResult = rowEstimationResult.get(columnIndex);

            // If the data type is different, the configurations can be ignored
            if (columnEstimationResult.left != estimatedDataType) {
                continue;
            }

            for (final var config : columnEstimationResult.right) {
                if (!resultMap.containsKey(config.getClass())) {
                    resultMap.put(config.getClass(), new HashMap<>());
                }

                var map = resultMap.get(config.getClass());
                if (map.isEmpty()) {
                    map.put(config, 1);
                } else {
                    var isPresent = false;
                    for (final var existingConfig : map.entrySet()) {
                        if (existingConfig.getKey().equals(config)) {
                            existingConfig.setValue(existingConfig.getValue() + 1);
                            isPresent = true;
                            break;
                        }
                    }
                    if (!isPresent) {
                        map.put(config, 1);
                    }
                }
            }

        }

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

    public List<String> normalizeColumnNames(final String[] columnNames) {
        return Arrays.stream(columnNames).map(columnName -> columnName.replace(" ", "_")).toList();
    }

    /**
     * Performs the counting for the Collection of datatype
     * estimations for every row.
     * @param datatypesForRows two-dimensional matrix; First dimension are the rows, second dimension the columns
     * @return A List of Count-Maps. For every Column a new Map entry is created
     */
    private List<Map<DataType, Integer>> countEstimatedDatatypesForRows(
            List<List<ImmutablePair<DataType, List<Configuration>>>> datatypesForRows) {
        List<List<DataType>> transposedList = extractDataTypes(datatypesForRows);
        List<Map<DataType, Integer>> countedMapsForColumns = new ArrayList<>();

        for (List<DataType> columnResults : transposedList) {
            countedMapsForColumns.add(countEstimatedDatatypesForColumn(columnResults));
        }

        return countedMapsForColumns;
    }

    /**
     * Counts the number of DataType occurences for a single column.
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
     * Transposes a given List<List<*>> structure.
     * That means that rows will be converted to
     * columns and columns to rows.
     *
     * @param list the 2d matrix to transpose
     * @return transposed matrix <List<List<*>>
     * @param <T> the Type of the object
     */
    public static <T> List<List<T>> transpose(List<List<T>> list) {
        final int N = list.stream().mapToInt(List::size).max().orElse(-1);
        List<Iterator<T>> iterList = list.stream().map(List::iterator).toList();
        return IntStream.range(0, N)
                .mapToObj(n -> iterList.stream()
                        .filter(Iterator::hasNext)
                        .map(Iterator::next)
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());
    }

    public static List<List<DataType>> extractDataTypes(List<List<ImmutablePair<DataType, List<Configuration>>>> list) {
        final int N = list.stream().mapToInt(List::size).max().orElse(-1);
        List<Iterator<ImmutablePair<DataType, List<Configuration>>>> iterList = list.stream().map(List::iterator).toList();
        return IntStream.range(0, N)
                        .mapToObj(n -> iterList.stream()
                                               .filter(Iterator::hasNext)
                                               .map(Iterator::next)
                                               .map(element -> element.left)
                                               .collect(Collectors.toList()))
                        .collect(Collectors.toList());
    }

    /**
     * Method that tries to convert a value for a given DataBuilder
     * @param builder the DataBuilder for a specific type
     * @param column the String Value
     * @return true if conversion was successful; false otherwise
     */
    private boolean tryConvertingDataType(
            DataBuilder builder,
            String column
    ) {
        final var estimationResult = builder.estimateColumnConfiguration(column);


        try {


            builder.setValue(column, new ArrayList<>()).build();
            return true;
        } catch (Exception e) {
            // Handle the exception if needed
            return false;
        }
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
     * Creates a list of DataTypes, where every type for a given
     * number of columns is set to UNDEFINED
     * @param numberOfColumns depicting the length of the list
     * @return List with DataTypes
     */
    public List<ImmutablePair<DataType, List<Configuration>>> getUndefinedDatatypesList(int numberOfColumns) {
        List<ImmutablePair<DataType, List<Configuration>>> result = new ArrayList<>();
        for (int i = 0; i < numberOfColumns; i++) {
            result.add(ImmutablePair.of(DataType.UNDEFINED, new ArrayList<>()));
        }

        return result;
    }

    /**
     * Builds a new DataConfiguration for a list of DataTypes.
     * The resulting DataConfiguration will contain a list of
     * ColumnConfigurations where only the DataType contains relevant
     * information
     * @param dataTypes list of datatypes to be processed
     * @param columnNames list containing the column names
     * @return new DataConfiguration object
     */
    public DataConfiguration buildConfigurationForDataTypes(
            final List<ImmutablePair<DataType, List<Configuration>>> dataTypes,
            List<String> columnNames
    ) {
        DataConfiguration resultingConfiguration = new DataConfiguration();
        List<ColumnConfiguration> resultingColumnConfigurations = new ArrayList<>();

        for (int i = 0; i < dataTypes.size(); i++) {
            DataType type = dataTypes.get(i).getLeft();
            String columnName = columnNames.get(i);
            List<Configuration> configurations = dataTypes.get(i).getRight();

            ColumnConfiguration newColumnConfiguration = new ColumnConfiguration(
                    i, columnName, type, type.getDefaultScale(), configurations
            );
            resultingColumnConfigurations.add(newColumnConfiguration);
        }
        resultingConfiguration.setConfigurations(resultingColumnConfigurations);

        return resultingConfiguration;
    }
}
