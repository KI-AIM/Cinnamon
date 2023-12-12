package de.kiaim.platform.processor;

import de.kiaim.platform.model.FileConfiguration;
import de.kiaim.platform.model.data.DataType;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.model.TransformationResult;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class CsvProcessor extends CommonDataProcessor implements DataProcessor{

    /**
     * {@inheritDoc}
     */
    @Override
    public TransformationResult read(InputStream data, FileConfiguration fileConfiguration,
                                     DataConfiguration configuration) {
        String csvString = getStringFromInputStream(data);

        return this.transformTwoDimensionalDataToDataSetAndValidate(csvString, fileConfiguration, configuration);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataConfiguration estimateDatatypes(InputStream data, FileConfiguration fileConfiguration) {
        String csvString = getStringFromInputStream(data);

        if (!csvString.isEmpty()) {
            List<String> validRows = getSubsetOfCompleteRows(csvString, fileConfiguration, 10);
            List<String> firstRow = getFirstRowFromCSV(csvString, fileConfiguration);

            List<DataType> estimatedDatatypes;
            if (!validRows.isEmpty()) {
                estimatedDatatypes = estimateDatatypesForMultipleRows(validRows, fileConfiguration);
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
     * @param fileConfiguration Configuration describing the csv string.
     * @param maxNumberOfRows the maximum number of rows
     * @return A List<String> of split rows
     */
    private List<String> getSubsetOfCompleteRows(String csvString, FileConfiguration fileConfiguration,
                                                 int maxNumberOfRows) {
        List<String> rows = Arrays.asList(csvString.split(fileConfiguration.getLineSeparator()));
        List<String> validRows = new ArrayList<>();

        int i = fileConfiguration.isHasHeader() ? 1 : 0;

        while (i < rows.size() && validRows.size() < maxNumberOfRows) {
            String row = rows.get(i);

            List<String> columns = Arrays.asList(row.split(fileConfiguration.getColumnSeparator()));

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
     * @param fileConfiguration Configuration describing the csv string.
     * @return A List<String> with the column values
     */
    private List<String> getFirstRowFromCSV(String csvString, FileConfiguration fileConfiguration) {
        int firstRowIndex = fileConfiguration.isHasHeader() ? 1 : 0;
        return Arrays.asList(
                Arrays.asList(
                        csvString.split(fileConfiguration.getLineSeparator())
                ).get(firstRowIndex).split(fileConfiguration.getColumnSeparator())
        );

    }

}
