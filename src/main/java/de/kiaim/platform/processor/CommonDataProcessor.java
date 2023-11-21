package de.kiaim.platform.processor;

import de.kiaim.platform.helper.DataTransformationHelper;
import de.kiaim.platform.helper.ExceptionToTransformationErrorMapper;
import de.kiaim.platform.model.*;
import de.kiaim.platform.model.data.*;
import de.kiaim.platform.model.data.configuration.ColumnConfiguration;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import lombok.Getter;
import lombok.Setter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
public abstract class CommonDataProcessor implements DataProcessor {

    //TODO: Fetch information from Frontend (?)
    @Setter
    private String columnSeparator = ",";
    @Setter
    private String lineSeparator = "\n";

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
     * @param config The data config that specifies the DataTypes (input from frontend)
     * @return TransformationResult with DataSet and errors
     */
    public TransformationResult transformTwoDimensionalDataToDataSetAndValidate(
            String data,
            DataConfiguration config
    ) {
        DataTransformationHelper dataHelper = new DataTransformationHelper();
        ExceptionToTransformationErrorMapper exceptionToTransformationErrorMapper =
                new ExceptionToTransformationErrorMapper();

        //Create objects to store results
        List<DataRow> dataRows = new ArrayList<>();
        List<DataRowTransformationError> errors = new ArrayList<>();

        //Split Strings at the line separator to receive a list with the row-strings
        List<String> rows = new ArrayList<>(Arrays.asList(data.split(getLineSeparator())));

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex ++) {
            // Process every row
            boolean errorInRow = false;
            String row = rows.get(rowIndex);

            // Split row to get list with different columns
            List<String> cols = new ArrayList<>(Arrays.asList(row.split(getColumnSeparator())));

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
                    Data transformedData = dataHelper.transformData(col, columnConfiguration);
                    transformedCol.add(transformedData);
                } catch (Exception e) {
                    // Transformation error that was thrown inside the Data builders
                    DataRowTransformationError newError = new DataRowTransformationError(rowIndex, cols);

                    //Resolve to error type, to easier parse information to frontend
                    TransformationErrorType errorType = exceptionToTransformationErrorMapper.mapException(e);
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
            if (!isValueNotEmpty(columnValue)) {
                columnIsValid = false;
            }
        }

        return columnIsValid;
    }

    /**
     * Checks whether a value is not empty
     * @param value to be checked
     * @return true if value is not empty; false otherwise
     */
    private boolean isValueNotEmpty(String value) {
        return !value.isEmpty() &&
                !value.equals("N/A") &&
                !value.equals("NaN") &&
                !value.equals("null");
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
     * @return new DataConfiguration object
     */
    public DataConfiguration buildConfigurationForDataTypes(List<DataType> dataTypes) {
        DataConfiguration resultingConfiguration = new DataConfiguration();
        List<ColumnConfiguration> resultingColumnConfigurations = new ArrayList<>();

        for (int i = 0; i < dataTypes.size(); i++) {
            DataType type = dataTypes.get(i);

            ColumnConfiguration newColumnConfiguration = new ColumnConfiguration(
                    i, "", type, new ArrayList<>()
            );
            resultingColumnConfigurations.add(newColumnConfiguration);
        }
        resultingConfiguration.setConfigurations(resultingColumnConfigurations);

        return resultingConfiguration;
    }

}
