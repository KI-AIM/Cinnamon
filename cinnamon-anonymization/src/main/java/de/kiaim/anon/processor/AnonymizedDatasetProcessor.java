package de.kiaim.anon.processor;

import de.kiaim.anon.exception.ResultDatasetProcessingException;
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
//     - interval generalization on numerical data. it shouldn‘t happen that intervals
//     are returned to the KI-AIM platform, should always be mean values, an exception is thrown in that case for now

    /**
     * Processes the anonymized data in format String[][] and converts it into a DataSet object,
     * using the original data configuration to map values to the correct data types.
     *
     *
     * @param anonymizedData The 2D array representing the anonymized data to be processed.
     * @param originalDataConfiguration The configuration that contains the original data type information.
     * @return A DataSet object containing the processed DataRows.
     */
    public static DataSet convertToDataSet(String[][] anonymizedData, DataConfiguration originalDataConfiguration)
            throws ResultDatasetProcessingException {
        List<DataRow> dataRows = new ArrayList<>();

        try {
            // Check if the first row contains column names
            boolean firstRowIsHeader = isHeaderRow(anonymizedData[0], originalDataConfiguration);
            int startRow = firstRowIsHeader ? 1 : 0;

            for (int rowIndex = startRow; rowIndex < anonymizedData.length; rowIndex++) {
                String[] row = anonymizedData[rowIndex];
                List<Data> dataList = new ArrayList<>();
                for (int i = 0; i < row.length; i++) {
                    String value = row[i];
                    DataType type = originalDataConfiguration.getConfigurations().get(i).getType();

                    // Check and correct value
                    value = checkAndCorrectValue(value, type);

                    // Convert value in a Data object and check that the type correspond to the value type
                    Data dataValue = createDataByTypeAndValue(type, parseValueByType(value, type));
                    dataList.add(dataValue);
                }
                dataRows.add(new DataRow(dataList));
            }

            return new DataSet(dataRows, originalDataConfiguration);
        } catch (Exception e) {
            // Catch any exception and wrap it in a ResultDatasetProcessingException
            throw new ResultDatasetProcessingException("An error occurred while processing the anonymized dataset:" + e.getMessage());
        }
    }

    /**
     * Checks if the first row of the data contains column headers by comparing the values in the row
     * with the column names defined in the provided data configuration.
     *
     * @param row The row of data to check.
     * @param dataConfiguration The data configuration containing the expected column names.
     * @return true if the first row contains headers, false otherwise.
     */
    private static boolean isHeaderRow(String[] row, DataConfiguration dataConfiguration) {
        for (int i = 0; i < row.length; i++) {
            if (!row[i].equalsIgnoreCase(dataConfiguration.getConfigurations().get(i).getName())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks and corrects a given value based on its expected data type.
     * It handles special cases such as "NULL" or "*" (which should be treated as null),
     * and throws an exception if the value contains intervals for numerical data types.
     *
     * @param value The value to check and correct.
     * @param type The expected data type of the value.
     * @return The corrected value, or null if the value was "NULL" or "*".
     */
    private static String checkAndCorrectValue(String value, DataType type) {
        // Check if value is NULL, "*" or contains only "*"
        if (isNullOrStar(value)) {
            return null;
        }

        // Check if interval for numerical values
        if (type == DataType.DECIMAL || type == DataType.INTEGER) {
            if (containsCommaInNumericalValue(value)) {
                throw new ResultDatasetProcessingException("Interval values are not allowed for numerical data.");
            }
        }

        // Check if value contains "*" but also other characters (e.g., "4,0***", "6***", "A**")
        // Replace '*' with '0' for numeric values or 'X' for string values
        if (containsStarWithOtherCharacters(value)) {
            // TODO : if DECIMAL value , just remove the *
            if (type == DataType.DECIMAL) {
                // Replace '*' with '' for decimal types
                value = value.replace("*", "");
            } else if (type == DataType.INTEGER) {
                // Replace '*' with '0' for integer types
                value = value.replace("*", "0");
            } else if (type == DataType.STRING) {
                // Replace '*' with 'X' for strings
                value = value.replace("*", "X");
            }
        }

        return value;
    }

    /**
     * Checks if the value is "NULL", "*" or contains only "*".
     *
     * @param value The value to check.
     * @return true if the value is "NULL", "*" or contains only "*", false otherwise.
     */
    private static boolean isNullOrStar(String value) {
        return "NULL".equalsIgnoreCase(value) || value.equals("*") || value.matches("[*]+");
    }

    /**
     * Checks if the value is an interval/contains a comma,
     * which is not allowed for numerical data types (DECIMAL or INTEGER).
     *
     * @param value The value to check.
     * @return true if the value contains a comma, false otherwise.
     */
    private static boolean containsCommaInNumericalValue(String value) {
        return value.contains(",");
    }

    /**
     * Checks if the value contains "*" but also other characters (e.g., "4,0***", "6***", "A**").
     *
     * @param value The value to check.
     * @return true if the value contains "*" but also other characters, false otherwise.
     */
    public static boolean containsStarWithOtherCharacters(String value) {
        return value.contains("*") && !value.matches("[*]+");
    }

    private static boolean isInterval(String value) {
        return value.contains("[") && value.contains(",");
    }

    /**
     * Parses a value into an object of the specified type.
     *
     * @param value The value to parse.
     * @param type The type to parse the value into.
     * @return The parsed object corresponding to the value and type.
     * @throws IllegalArgumentException if the value is invalid for the specified type.
     */
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
