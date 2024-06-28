package de.kiaim.anon.service;

import de.kiaim.model.configuration.anonymization.AttributeConfig;
import de.kiaim.model.configuration.anonymization.DatasetAnonymizationConfig;
import de.kiaim.model.configuration.data.ColumnConfiguration;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.data.*;
import de.kiaim.model.enumeration.DataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Class for compatibility assurance of objects needed to call JAL
 * before calling the anonymization process.
*/
public class CompatibilityAssurance {
    /**
     * Check the compatibility between a dataset configuration and rows.
     * @param dataSet The DataSet to check.
     * @throws IllegalArgumentException If there is a mismatch in the number of data elements or data types.
     */
    public static void checkDataSetCompatibility(DataSet dataSet) {
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
                    throw new IllegalArgumentException("Data type mismatch at column " + i + ". Expected: " + expectedDataType + ", Found: " + data.getClass().getSimpleName());
                }
            }
        }
    }

    /**
     * Check the compatibility between an anonymization configuration and a data configuration.
     * This method ensures that the attribute configurations in the anonymization configuration
     * match the column configurations in the data configuration.
     *
     * @param anonymizationConfig The anonymization configuration to check.
     * @param dataConfiguration The data configuration to check.
     * @throws IllegalArgumentException if the number of attributes in the anonymization configuration
     *                                  differs from the number of columns in the data configuration.
     * @throws IllegalArgumentException if the index of an attribute in the anonymization configuration
     *                                  differs from the corresponding index in the data configuration.
     * @throws IllegalArgumentException if the data type of an attribute in the anonymization configuration
     *                                  differs from the corresponding data type in the data configuration.
     * @throws IllegalArgumentException if the scale of an attribute in the anonymization configuration
     *                                  differs from the corresponding scale in the data configuration.
     */
    public static void checkAnonAndDataConfigCompatibility(
            DatasetAnonymizationConfig anonymizationConfig, DataConfiguration dataConfiguration){
        if (anonymizationConfig.getAttributeConfigurations().size() != dataConfiguration.getConfigurations().size()) {
            throw new IllegalArgumentException(anonymizationConfig.getAttributeConfigurations().size() +
                    "attributes found in anon config while dataset contains " + dataConfiguration.getConfigurations().size() + " attributes.");
        }

        for (AttributeConfig attributeConfig : anonymizationConfig.getAttributeConfigurations()){
            String name = attributeConfig.getName();
            ColumnConfiguration columnConfiguration = dataConfiguration.getColumnConfigurationByColumnName(name);
            if (attributeConfig.getIndex() != columnConfiguration.getIndex()){
                throw new IllegalArgumentException(anonymizationConfig.getAttributeConfigurations().size() +
                        "attributes found in anon config while dataset contains " + dataConfiguration.getConfigurations().size() + " attributes.");
            }
//            if (attributeConfig.getDataType() != columnConfiguration.getType()){
//                throw new IllegalArgumentException(name + " attributes is of type " + attributeConfig.getAttributeType()
//                + " in anon config but "+ columnConfiguration.getType() + " in data config.");
//            }
//            if (attributeConfig.getScale() != columnConfiguration.getScale()){
//                throw new IllegalArgumentException(name + " attributes is of scale " + attributeConfig.getScale()
//                        + " in anon config but "+ columnConfiguration.getScale() + " in data config.");
//            }
        }
    }

    /**
     * Helper function to determine if the data type of Data object is compatible with the expected data type.
     */
    private static boolean isDataTypeCompatible(Data data, DataType expectedDataType) {
        switch (expectedDataType) {
            case STRING:
                return data instanceof StringData;
            case INTEGER:
                return data instanceof IntegerData;
            case DECIMAL:
                return data instanceof DecimalData;
            case BOOLEAN:
                return data instanceof BooleanData;
            case DATE:
                return data instanceof DateData;
            case DATE_TIME:
                return data instanceof DateTimeData;
            default:
                return false;
        }
    }

    /**
     * Check that all specified column names exist in the given DataConfiguration.
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

    /**
     * Check that the indices of the columns in the provided DataConfiguration are sequential and start from 0.
     *
     * @param config The DataConfiguration object containing the column configurations to be verified.
     * @throws IllegalArgumentException If the column indices are not sequential starting from 0 or if there are gaps in the sequence.
     */
    public static void verifyColumnIndices(DataConfiguration config) {
        int numberOfColumns = config.getConfigurations().size();
        boolean[] isPresentIndex = new boolean[numberOfColumns];

        for (ColumnConfiguration columnConfig : config.getConfigurations()) {
            if (columnConfig.getIndex() < 0 || columnConfig.getIndex() >= numberOfColumns) {
                throw new IllegalArgumentException("Column index out of range: " + columnConfig.getIndex());
            }
            isPresentIndex[columnConfig.getIndex()] = true;
        }

        List<Integer> missingIndices = new ArrayList<>();
        for (int i = 0; i < isPresentIndex.length; i++) {
            if (!isPresentIndex[i]) {
                missingIndices.add(i);
            }
        }
        if (!missingIndices.isEmpty()) {
            throw new IllegalArgumentException("Missing column indices: " + missingIndices);
        }
    }

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
