package de.kiaim.platform.processor;

import de.kiaim.platform.model.data.DataType;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.model.TransformationResult;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CsvProcessor extends CommonDataProcessor implements DataProcessor{
    /**
     * {@inheritDoc}
     */
    @Override
    public TransformationResult read(InputStream data, DataConfiguration configuration) {
        String csvString = getStringFromInputStream(data);

        return this.transformTwoDimensionalDataToDataSetAndValidate(csvString, configuration);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataConfiguration estimateDatatypes(InputStream data) {
        String csvString = getStringFromInputStream(data);

        if (!csvString.isEmpty()) {
            List<String> validRows = getSubsetOfCompleteRows(csvString, 10);
            List<String> firstRow = getFirstRowFromCSV(csvString);

            List<DataType> estimatedDatatypes;
            if (!validRows.isEmpty()) {
                estimatedDatatypes = estimateDatatypesForMultipleRows(validRows);
            } else {
                estimatedDatatypes = getUndefinedDatatypesList(firstRow.size());
            }

            return buildConfigurationForDataTypes(estimatedDatatypes);

        } else {
            return new DataConfiguration();
        }
    }


    /**
     * Function that returns a subset of complete rows for a csvString.
     * Complete means that no missing value should be present in a row.
     * The amount of rows is limited by the parameter maxNumberOfRows.
     * @param csvString The csv String
     * @param maxNumberOfRows the maximum number of rows
     * @return A List<String> of split rows
     */
    private List<String> getSubsetOfCompleteRows(String csvString, int maxNumberOfRows) {
        List<String> rows = Arrays.asList(csvString.split(getLineSeparator()));
        List<String> validRows = new ArrayList<>();

        int i = 0;

        while (i < rows.size() && validRows.size() < maxNumberOfRows) {
            String row = rows.get(i);

            List<String> columns = Arrays.asList(row.split(getColumnSeparator()));

            if (isColumnListComplete(columns)) {
                validRows.add(row);
            }
            i++;
        }

        return validRows;
    }

    /**
     * Returns the first row of a csv string as a split List
     * @param csvString the csv String
     * @return A List<String> with the column values
     */
    private List<String> getFirstRowFromCSV(String csvString) {
        return Arrays.asList(
                Arrays.asList(
                        csvString.split(getLineSeparator())
                ).get(0).split(getColumnSeparator())
        );

    }

}
