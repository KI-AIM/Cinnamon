package de.kiaim.platform.processor;

import de.kiaim.platform.helper.DataTransformationHelper;
import de.kiaim.platform.helper.ExceptionToTransformationErrorMapper;
import de.kiaim.platform.model.*;
import de.kiaim.platform.model.data.*;
import de.kiaim.platform.model.data.configuration.ColumnConfiguration;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Getter
public abstract class CommonDataProcessor implements DataProcessor {

    @Autowired
    DataTransformationHelper dataTransformationHelper;

    @Autowired
    ExceptionToTransformationErrorMapper exceptionToTransformationErrorMapper;

    /**
     * Transforms a two-dimensional string dataset into the internal
     * DataSet object.
     * Upon transformation each value is validated. If a fault has been detected
     * the error will be added to the DataRowTransformationError list with the
     * corresponding raw row string and the row index.
     * If an error was detected for any value in a row, the complete row will
     * not be added to the DataSet.
     *
     * The transformed DataSet and the errors are added to a TransformationResult
     * and returned.
     * @param data the raw data string for two-dimensional data (CSV-separated)
     * @param fileConfiguration The config that specifies the format of the data string.
     * @param config The data config that specifies the DataTypes (input from frontend)
     * @return TransformationResult with DataSet and errors
     */
    public TransformationResult transformTwoDimensionalDataToDataSetAndValidate(
            String data,
            FileConfiguration fileConfiguration,
            DataConfiguration config
    ) {
        //Create objects to store results
        List<DataRow> dataRows = new ArrayList<>();
        List<DataRowTransformationError> errors = new ArrayList<>();

        //Split Strings at the line separator to receive a list with the row-strings
        List<String> rows = new ArrayList<>(Arrays.asList(data.split(fileConfiguration.getLineSeparator())));

        int startRow = fileConfiguration.isHasHeader() ? 1 : 0;
        for (int rowIndex = startRow; rowIndex < rows.size(); rowIndex ++) {
            // Process every row
            boolean errorInRow = false;
            String row = rows.get(rowIndex);

            // Split row to get list with different columns
            List<String> cols = new ArrayList<>(Arrays.asList(row.split(fileConfiguration.getColumnSeparator())));

            List<Data> transformedCol = new ArrayList<>();

            for (int colIndex = 0; colIndex < cols.size(); colIndex++) {
                // Process every column value in a row
                String col = cols.get(colIndex);

                List<ColumnConfiguration> matchingColumnConfigurations =
                        getColumnConfigurationForIndex(config.getConfigurations(), colIndex);

                // Every index should appear exactly once
                assert !matchingColumnConfigurations.isEmpty();
                ColumnConfiguration columnConfiguration = matchingColumnConfigurations.get(0);

                try {
                    Data transformedData = this.dataTransformationHelper.transformData(col, columnConfiguration);
                    transformedCol.add(transformedData);
                } catch (Exception e) {
                    // Transformation error that was thrown inside the Data builders
                    DataRowTransformationError newError = new DataRowTransformationError(rowIndex, cols);

                    //Resolve to error type, to easier parse information to frontend
                    TransformationErrorType errorType = this.exceptionToTransformationErrorMapper.mapException(e);
                    newError.addError(new DataTransformationError(colIndex, errorType));

                    //Add error to errorList
                    errors.add(newError);
                    // set flag to not add row to DataSet
                    errorInRow = true;
                }
            }

            // If no error was found, add result to DataRows
            if (!errorInRow) {
                dataRows.add(new DataRow(transformedCol));
            }

        }

        return new TransformationResult(new DataSet(dataRows, config), errors);
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
     * Transforms an InputStream to a processable String
     * @param inputStream to be processed
     * @return String representation
     */
    public String getStringFromInputStream(InputStream inputStream) {
        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            int c;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        } catch (IOException e) {
            //TODO Catch error
            throw new RuntimeException(e);
        }

        return textBuilder.toString();
    }

    /**
     * Check whether every value of a column, stored in a list,
     * is valid and not empty
     * @param column List to be processed
     * @return true, if column is complete; false otherwise
     */
    public boolean isColumnListComplete(List<String> column) {
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
     * available datatypes. The first valid transformation determines the datatype
     * estimation
     * @param row represented by a List with separated column values
     * @return List of DataTypes for every column
     */
    public List<DataType> estimateDatatypesFromRow(List<String> row) {
        List<DataType> result = new ArrayList<>();

        for (String column : row) {
            List<Pair<DataType, DataBuilder>> processingOrder = getProcessingOrder();
            boolean foundMatchingDataType = false;

            for (int i = 0; i < processingOrder.size() && !foundMatchingDataType ; i++) {
                Pair<DataType, DataBuilder> processor = processingOrder.get(i);

                foundMatchingDataType = tryConvertingDataType(processor.element1(), column);

                if(foundMatchingDataType) {
                    result.add(processor.element0());
                }
            }
            if (!foundMatchingDataType) {
                // If no processor was able to transform value it is UNDEFINED
                result.add(DataType.UNDEFINED);
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
     * @param rows The line split rows
     * @param fileConfiguration Configuration describing the rows.
     * @return List of datatype estimations
     */
    public List<DataType> estimateDatatypesForMultipleRows(List<String> rows, FileConfiguration fileConfiguration) {
        List<List<DataType>> datatypesForRows = new ArrayList<>();
        List<DataType> resultList = new ArrayList<>();

        for (String rowString : rows) {
            List<String> splittedRow = Arrays.asList(rowString.split(fileConfiguration.getColumnSeparator()));

            datatypesForRows.add(estimateDatatypesFromRow(splittedRow));
        }

        List<Map<DataType, Integer>> countedEstimatedDatatypes = countEstimatedDatatypesForRows(datatypesForRows);

        for (Map<DataType, Integer> countedMapForColumn : countedEstimatedDatatypes) {
            resultList.add(getMostEstimatedDatatypeFromCountMap(countedMapForColumn));
        }

        return resultList;
    }

    /**
     * Performs the counting for the Collection of datatype
     * estimations for every row.
     * @param datatypesForRows two-dimensional matrix; First dimension are the rows, second dimension the columns
     * @return A List of Count-Maps. For every Column a new Map entry is created
     */
    private List<Map<DataType, Integer>> countEstimatedDatatypesForRows(List<List<DataType>> datatypesForRows) {
        List<List<DataType>> transposedList = transpose(datatypesForRows);
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
    public List<DataType> getUndefinedDatatypesList(int numberOfColumns) {
        List<DataType> result = new ArrayList<>();
        for (int i = 0; i < numberOfColumns; i++) {
            result.add(DataType.UNDEFINED);
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
    public DataConfiguration buildConfigurationForDataTypes(List<DataType> dataTypes, List<String> columnNames) {
        DataConfiguration resultingConfiguration = new DataConfiguration();
        List<ColumnConfiguration> resultingColumnConfigurations = new ArrayList<>();

        for (int i = 0; i < dataTypes.size(); i++) {
            DataType type = dataTypes.get(i);
            String columnName = columnNames.get(i);

            ColumnConfiguration newColumnConfiguration = new ColumnConfiguration(
                    i, columnName, type, new ArrayList<>()
            );
            resultingColumnConfigurations.add(newColumnConfiguration);
        }
        resultingConfiguration.setConfigurations(resultingColumnConfigurations);

        return resultingConfiguration;
    }
}
