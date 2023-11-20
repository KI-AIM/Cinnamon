package de.kiaim.platform.processor;

import de.kiaim.platform.helper.DataTransformationHelper;
import de.kiaim.platform.helper.ExceptionToTransformationErrorMapper;
import de.kiaim.platform.model.*;
import de.kiaim.platform.model.data.Data;
import de.kiaim.platform.model.data.DataRow;
import de.kiaim.platform.model.data.configuration.ColumnConfiguration;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import lombok.Getter;
import lombok.Setter;

import java.io.InputStream;
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

    abstract public TransformationResult read(InputStream data);

    abstract public DataConfiguration estimateDatatypes(InputStream data);

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
        ExceptionToTransformationErrorMapper exceptionToTransformationErrorMapper = new ExceptionToTransformationErrorMapper();

        //Create objects to store results
        List<DataRow> dataRows = new ArrayList<>();
        List<DataRowTransformationError> errors = new ArrayList<>();

        //Split Strings at the line separator to receive a list with the row-strings
        List<String> rows = new ArrayList<>(Arrays.asList(data.split(lineSeparator)));

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex ++) {
            // Process every row
            boolean errorInRow = false;
            String row = rows.get(rowIndex);

            List<String> cols = new ArrayList<>(Arrays.asList(row.split(columnSeparator)));

            List<Data> transformedCol = new ArrayList<>();

            for (int colIndex = 0; colIndex < cols.size(); colIndex++) {
                // Process every column in a row
                String col = cols.get(colIndex);
                int finalColIndex = colIndex; //Variable in lambda expression must be effectively final

                List<ColumnConfiguration> matchingColumnConfigurations = config.getConfigurations()
                        .stream()
                        .filter(it -> it.getIndex() == finalColIndex)
                        .toList();

                // Every index should appear exactly once
                assert !matchingColumnConfigurations.isEmpty();
                ColumnConfiguration columnConfiguration = matchingColumnConfigurations.get(0);

                try {
                    Data transformedData = dataHelper.transformData(col, columnConfiguration);
                    transformedCol.add(transformedData);
                } catch (Exception e) {
                    // Transformation error that was thrown inside the Data builders
                    DataRowTransformationError newError = new DataRowTransformationError(rowIndex, cols);

                    TransformationErrorType errorType = exceptionToTransformationErrorMapper.mapException(e);
                    newError.addError(new DataTransformationError(colIndex, errorType));

                    errors.add(newError);
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

}
