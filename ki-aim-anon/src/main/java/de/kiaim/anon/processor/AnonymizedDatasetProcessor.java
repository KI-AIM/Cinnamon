package de.kiaim.anon.processor;

import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.data.*;
import de.kiaim.model.enumeration.DataType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Build DataSet object from String[][] result of anonymized dataset.
 * Check Data valus validity with original DataConfiguration.
 *
 */
public class AnonymizedDatasetProcessor {
    //    Need to check that the data type before and after data anon didn't change.
//    Small functions to check specific transformations and if append correct it.
//    TODO :
//     -Checking if NULL or * values were added that changed numerical data to categorical data
//      and need to be replaced by „proper“ NA values as used by KI-AIM backend
//     -Check if original DataConfig match Anonymized DataType
//     -For masking on numerical values, making sure that e.g. the * are removed(???)
//      so only numbers remain and the data type is consistent to the data before protection
//     - interval generalization on numerical data. it shouldn‘t happen that for KI-AIM intervalls
//     are returned, should always be mean values. just throw an exception in that case for now

    public static DataSet convertToDataSet(String[][] anonymizedData, DataConfiguration originalDataConfiguration) {
        List<DataRow> dataRows = new ArrayList<>();

        for (String[] row : anonymizedData) {
            List<Data> dataList = new ArrayList<>();
            for (int i = 0; i < row.length; i++) {
                String value = row[i];
                DataType type = originalDataConfiguration.getConfigurations().get(i).getType();
                // TODO : Check that value meet dataType

                // Check and correct value
                value = checkAndCorrectValue(value, type);

                // Convert value in Data
                Data dataValue = convertStringToData(value, type);
                dataList.add(dataValue);
            }
            dataRows.add(new DataRow(dataList));
        }

        return new DataSet(dataRows, originalDataConfiguration);
    }

    private static String checkAndCorrectValue(String value, DataType type) {
        // Check if value is NULL or *
        if ("NULL".equalsIgnoreCase(value) || "*".equals(value)) {
            return null;
        }

        // Check if interval for numerical values
        if (type == DataType.DECIMAL || type == DataType.INTEGER) {
            if (value.contains(",")) {
                throw new IllegalArgumentException("Interval values are not allowed for numerical data.");
            }
        }

        return value;
    }

    private static Data convertStringToData(String value, DataType type) {
        try {
            return switch (type) {
                case BOOLEAN -> new BooleanData(Boolean.parseBoolean(value));
                case DATE -> new DateData(LocalDate.parse(value));
                case DATE_TIME -> new DateTimeData(LocalDateTime.parse(value));
                case DECIMAL -> new DecimalData(Float.parseFloat(value));
                case INTEGER -> new IntegerData(Integer.parseInt(value));
                case STRING -> new StringData(value);
                default -> throw new IllegalArgumentException("Unknown data type: " + type);
            };
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid value for type " + type + ": " + value, e);
        }
    }
}
