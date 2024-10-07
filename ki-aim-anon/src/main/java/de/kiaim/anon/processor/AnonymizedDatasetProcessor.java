package de.kiaim.anon.processor;

import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.data.Data;
import de.kiaim.model.data.DataRow;
import de.kiaim.model.data.DataSet;
import de.kiaim.model.enumeration.DataType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static de.kiaim.anon.helper.DataGeneration.createDataByTypeAndValue;

/**
 * Build DataSet object from String[][] result of anonymized dataset.
 * Check Data values validity with original DataConfiguration.
 *
 */
@Component
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

        // Check if the first row contains column names
        boolean firstRowIsHeader = isHeaderRow(anonymizedData[0], originalDataConfiguration);
        int startRow = firstRowIsHeader ? 1 : 0;

        for (int rowIndex = startRow; rowIndex < anonymizedData.length; rowIndex++) {
            String[] row = anonymizedData[rowIndex];
            List<Data> dataList = new ArrayList<>();
            for (int i = 0; i < row.length; i++) {
                String value = row[i];
                DataType type = originalDataConfiguration.getConfigurations().get(i).getType();
                // TODO : Check that value meet dataType

                // Check and correct value
                value = checkAndCorrectValue(value, type);

                // Convert value in Data
                Data dataValue = createDataByTypeAndValue(type, parseValueByType(value, type));
                dataList.add(dataValue);
            }
            dataRows.add(new DataRow(dataList));
        }

        return new DataSet(dataRows, originalDataConfiguration);
    }

    private static boolean isHeaderRow(String[] row, DataConfiguration dataConfiguration) {
        for (int i = 0; i < row.length; i++) {
            if (!row[i].equalsIgnoreCase(dataConfiguration.getConfigurations().get(i).getName())) {
                return false;
            }
        }
        return true;
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

    private static boolean isInterval(String value) {
        return value.contains("[") && value.contains(",");
    }

    private static Object parseValueByType(String value, DataType type) {
        if (value == null) {
            return switch (type) {
                case BOOLEAN -> null;
                case DATE -> null;
                case DATE_TIME -> null;
                case DECIMAL -> null;
                case INTEGER -> null;
                case STRING -> null;
                default -> throw new IllegalArgumentException("Unknown data type: " + type);
            };
        }

        try {
            return switch (type) {
                case BOOLEAN -> Boolean.parseBoolean(value);
                case DATE -> LocalDate.parse(value);
                case DATE_TIME -> LocalDateTime.parse(value);
                case DECIMAL -> Float.parseFloat(value);
                case INTEGER -> Integer.parseInt(value);
                case STRING -> value;
                default -> throw new IllegalArgumentException("Unknown data type: " + type);
            };
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid value for type " + type + ": " + value, e);
        }
    }
}
