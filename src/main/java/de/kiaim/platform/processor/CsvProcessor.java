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
            List<String> firstValidRow = getFirstValidRowFromCSV(csvString);
            List<String> firstRow = getFirstRowFromCSV(csvString);

            List<DataType> estimatedDatatypes;
            if (!firstValidRow.isEmpty()) {
                estimatedDatatypes = estimateDatatypesFromRow(firstValidRow);
            } else {
                estimatedDatatypes = getUndefinedDatatypesList(firstRow.size());
            }

            return buildConfigurationForDataTypes(estimatedDatatypes);

        } else {
            return new DataConfiguration();
        }
    }

    private List<String> getFirstValidRowFromCSV(String csvString) {
        List<String> rows = Arrays.asList(csvString.split(getLineSeparator()));
        List<String> firstValidRow = new ArrayList<>();

        int i = 0;
        boolean foundValidRow = false;

        while (i < rows.size() && !foundValidRow) {
            String row = rows.get(i);

            List<String> columns = Arrays.asList(row.split(getColumnSeparator()));

            if (isColumnListComplete(columns)) {
                foundValidRow = true;
                firstValidRow = columns;
            }
            i++;
        }

        return firstValidRow;
    }

    private List<String> getFirstRowFromCSV(String csvString) {
        return Arrays.asList(
                Arrays.asList(
                        csvString.split(getLineSeparator())
                ).get(0).split(getColumnSeparator())
        );

    }





}
