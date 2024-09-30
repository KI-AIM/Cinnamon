package de.kiaim.anon.service;

import de.kiaim.model.configuration.anonymization.AnonymizationConfig;
import de.kiaim.model.configuration.anonymization.AttributeConfig;
import de.kiaim.model.configuration.anonymization.frontend.FrontendAnonConfig;
import de.kiaim.model.configuration.data.ColumnConfiguration;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.data.*;
import de.kiaim.model.enumeration.DataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Class for compatibility assurance.
 * Check that every column of the DataSet have a matching configuration.
*/
public class CompatibilityAssurance {

    /**
     * Check the compatibility between a DataSet and a FrontendConfiguration.
     * All attributes of the DataSet ust have a matching configuration,
     * with the same name, dataType and dataScale.
     * Used before calling anonymization process.
     * @param dataSet The DataSet to check.
     * @throws IllegalArgumentException If there is a mismatch in the number of data elements or data types.
     */
    public static void checkDataSetAndFrontendConfigCompatibility(DataSet dataSet, FrontendAnonConfig frontendAnonConfig) {

    }
//
//    /**
//     * Check the compatibility between a dataset configuration and rows.
//     * @param dataSet The DataSet to check.
//     * @throws IllegalArgumentException If there is a mismatch in the number of data elements or data types.
//     */
//    public static void checkDataSetCompatibility(DataSet dataSet) {
//        List<ColumnConfiguration> configurations = dataSet.getDataConfiguration().getConfigurations();
//        int expectedNumberOfColumns = configurations.size();
//
//        for (DataRow row : dataSet.getDataRows()) {
//            List<Data> rowData = row.getData();
//
//            // Check that the number of data items in the row corresponds to the number of columns in configuration
//            if (rowData.size() != expectedNumberOfColumns) {
//                throw new IllegalArgumentException("Mismatch in number of columns. Expected: " + expectedNumberOfColumns + ", Found: " + rowData.size());
//            }
//
//            // Check the type of each data item against its column configuration
//            for (int i = 0; i < rowData.size(); i++) {
//                Data data = rowData.get(i);
//                DataType expectedDataType = configurations.get(i).getType();
//                // TODO : handle null data case
//                if (!isDataTypeCompatible(data, expectedDataType)) {
//                    throw new IllegalArgumentException("Data type mismatch at column " + i + ". Expected: " + expectedDataType + ", Found: " + data.getClass().getSimpleName());
//                }
//            }
//        }
//    }
//
    /**
     * Check the compatibility between a dataset configuration and rows.
     * Used to check the anonymized DataSet (anonymization result).
     * @param dataSet The DataSet to check.
     * @throws IllegalArgumentException If there is a mismatch in the number of data elements or data types.
     */
    public static Boolean isDataSetCompatible(DataSet dataSet) {
        List<ColumnConfiguration> configurations = dataSet.getDataConfiguration().getConfigurations();
        int expectedNumberOfColumns = configurations.size();

        for (DataRow row : dataSet.getDataRows()) {
            List<Data> rowData = row.getData();

            // Check that the number of data items in the row corresponds to the number of columns in configuration
            if (rowData.size() != expectedNumberOfColumns) {
                throw new IllegalArgumentException("Mismatch in number of columns. Expected: " + expectedNumberOfColumns + ", Found: " + rowData.size());
            }

            // Check the type of each data item against its column configuration
            for (int i = 0; i < rowData.size(); i++) {
                Data data = rowData.get(i);
                DataType expectedDataType = configurations.get(i).getType();
                // TODO : handle null data case
                if (!isDataTypeCompatible(data, expectedDataType)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Helper function to determine if the data type of Data object is compatible with the expected data type.
     */
    private static boolean isDataTypeCompatible(Data data, DataType expectedDataType) {
        return switch (expectedDataType) {
            case STRING -> data instanceof StringData;
            case INTEGER -> data instanceof IntegerData;
            case DECIMAL -> data instanceof DecimalData;
            case BOOLEAN -> data instanceof BooleanData;
            case DATE -> data instanceof DateData;
            case DATE_TIME -> data instanceof DateTimeData;
            default -> false;
        };
    }

    /**
     * Check that all specified column names exist in the given DataConfiguration.
     * Used in DataSetTransformation class.
     *
     * @param config The DataConfiguration to check against.
     * @param columnNames Variable number of strings representing the column names to validate.
     * @throws IllegalArgumentException If any specified column name does not exist in the DataConfiguration.
     */
    public static void validateColumnExistence(DataConfiguration config, String... columnNames) {
        for (String columnName : columnNames) {
            if (config.getColumnConfigurationByColumnName(columnName) == null) {
                throw new IllegalArgumentException("Column '" + columnName + "' does not exist in the DataSet.");
            }
        }
    }
//
//    /**
//     * Check that the indices of the columns in the provided DataConfiguration are sequential and start from 0.
//     *
//     * @param config The DataConfiguration object containing the column configurations to be verified.
//     * @throws IllegalArgumentException If the column indices are not sequential starting from 0 or if there are gaps in the sequence.
//     */
//    public static void verifyColumnIndices(DataConfiguration config) {
//        int numberOfColumns = config.getConfigurations().size();
//        boolean[] isPresentIndex = new boolean[numberOfColumns];
//
//        for (ColumnConfiguration columnConfig : config.getConfigurations()) {
//            if (columnConfig.getIndex() < 0 || columnConfig.getIndex() >= numberOfColumns) {
//                throw new IllegalArgumentException("Column index out of range: " + columnConfig.getIndex());
//            }
//            isPresentIndex[columnConfig.getIndex()] = true;
//        }
//
//        List<Integer> missingIndices = new ArrayList<>();
//        for (int i = 0; i < isPresentIndex.length; i++) {
//            if (!isPresentIndex[i]) {
//                missingIndices.add(i);
//            }
//        }
//        if (!missingIndices.isEmpty()) {
//            throw new IllegalArgumentException("Missing column indices: " + missingIndices);
//        }
//    }
//
    /**
     * Check compatibility between 2 columns before merging.
     *
     * @param column1 DataSet column.
     * @param column2 Another DataSet column.
     * @throws IllegalArgumentException If any specified column name does not exist in the DataConfiguration.
     */
    public static void checkColumnsCompatibility(ColumnConfiguration column1, ColumnConfiguration column2) {
        if( column1.getScale() != column2.getScale()) {
            throw new IllegalArgumentException("Columns "+ column1.getName()+" and "+ column2.getName()+
                    " have different Scale");
        } else if (column1.getType() != column2.getType()) {
            throw new IllegalArgumentException("Columns "+ column1.getName()+" and "+ column2.getName()+
                    " Have different DataType");
        } else if (!column1.getConfigurations().equals(column2.getConfigurations())) {
            throw new IllegalArgumentException("Columns "+ column1.getName()+" and "+ column2.getName()+
                    " have different configurations");
        }
    }
}
