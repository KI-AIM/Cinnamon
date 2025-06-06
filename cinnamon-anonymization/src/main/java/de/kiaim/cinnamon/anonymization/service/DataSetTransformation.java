package de.kiaim.cinnamon.anonymization.service;

import de.kiaim.cinnamon.anonymization.helper.DataGeneration;
import de.kiaim.cinnamon.anonymization.model.dataSetTransformation.DataSetSplittingInformation;
import de.kiaim.cinnamon.anonymization.model.dataSetTransformation.MergedDataSetTraceability;
import de.kiaim.cinnamon.anonymization.model.dataSetTransformation.MergedDataSetTraceabilityList;
import de.kiaim.cinnamon.model.configuration.data.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.data.Data;
import de.kiaim.cinnamon.model.data.DataRow;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.model.data.StringData;
import de.kiaim.cinnamon.model.enumeration.DataType;
import lombok.Getter;

import java.util.*;

import static de.kiaim.cinnamon.anonymization.helper.MergeDataSets.mergeLeftAndRightDatasets;
import static de.kiaim.cinnamon.anonymization.service.CompatibilityAssurance.validateColumnExistence;

/**
 * This class contains methods to apply transformation on a DataSet.
 * Such format transformation (wide-long), datasets merging, dataset splitting etc
 */
@Getter
public class DataSetTransformation {
//    constructor
//    function :Take in table in longitudinal format and transform into wide table format
//    function : Reverse (wide -> long)
//    function : takes in list of datasets and merge it
//    function : possibility to split the table again
//    function : append dataset horizontally
//    function : add dataset vertically (not needed, but nice to have for processing stream data)
//    TODO : develop with unit test, each function should have at least one test where it works
//      and one test intended to fail
    private final DataSet dataSet;
    private final List<DataSet> dataSets;

    public DataSetTransformation(DataSet dataSet) {
        this.dataSet = dataSet;
        this.dataSets = null;
    }

    public DataSetTransformation(List<DataSet> dataSets) {
        if (dataSets.size() == 1){
            this.dataSet = dataSets.get(0);
            this.dataSets = null;
        } else {
            this.dataSets = dataSets;
            this.dataSet = null;
        }
    }

    /**
     * Transforms a DataSet from long format to wide format using the specified columns.
     * This method creates a new DataSet where each unique value of the columnsColumn
     * becomes a separate column in the output. Each row in the new DataSet corresponds to a unique
     * value of indexColumn from the original DataSet, with the cells filled by the corresponding
     * values mapped from valuesColumn.
     *
     * @param indexColumn The name of the column in the original DataSet that will act as the index
     *                    of the new DataSet. Each unique value in this column becomes a separate row
     *                    in the resulting DataSet.
     * @param columnsColumn The name of the column in the original DataSet whose unique values will
     *                      determine the columns in the new DataSet.
     * @param prefix Optional prefix for new column names (to prevent name clashes).
     * @param valuesColumn The name of the column that contains the values to be redistributed
     *                     into the new columns created from {@code columnsColumn} unique values.
     * @return A new DataSet in wide format, where each row represents a unique index, and each column
     *         represents one of the possible values from {@code columnsColumn}, filled with values
     *         from {@code valuesColumn}.
     * @throws IllegalArgumentException If any of the specified columns do not exist in the original DataSet.
     */
    // TODO : handle several valuesColumn ? Handle several columnsColumn ?
    public DataSet pivot(String indexColumn, String columnsColumn, String prefix, String valuesColumn) {
        // Check if given columns exist in dataset
        DataConfiguration config = dataSet.getDataConfiguration();
        validateColumnExistence(config, indexColumn, columnsColumn, valuesColumn);

        // Mapping from index values to their corresponding data rows in the new DataSet
        Map<Object, Map<Object, Object>> pivotedData = new HashMap<>();

        ColumnConfiguration indexConfiguration = Objects.requireNonNull(
                dataSet.getDataConfiguration().getColumnConfigurationByColumnName(indexColumn));
        ColumnConfiguration columnsConfiguration = Objects.requireNonNull(
                dataSet.getDataConfiguration().getColumnConfigurationByColumnName(columnsColumn));
        ColumnConfiguration valuesConfiguration = Objects.requireNonNull(
                dataSet.getDataConfiguration().getColumnConfigurationByColumnName(valuesColumn));

        // Prepare the new DataConfiguration
        DataConfiguration newConfig = new DataConfiguration();
        newConfig.addColumnConfiguration(new ColumnConfiguration(
                0,
                indexConfiguration.getName(),
                indexConfiguration.getType(),
                indexConfiguration.getScale(),
                indexConfiguration.getConfigurations()));

        // Temporary store to track column order in the pivoted DataSet
        Map<Object, Integer> columnMap = new HashMap<>();

        // Process each row in the original DataSet
        for (DataRow row : dataSet.getDataRows()) {
            Object indexValue = row.getData().get(indexConfiguration.getIndex()).getValue();
            Object columnValue = row.getData().get(columnsConfiguration.getIndex()).getValue();
            Object value = row.getData().get(valuesConfiguration.getIndex()).getValue();

            String newColumnName = prefix + columnValue.toString();

            // If new value met, create new column with name = columnValue value and dataType=valuesColumn type
            if (!columnMap.containsKey(newColumnName)) {
                columnMap.put(newColumnName, columnMap.size() + 1);  // Start adding from 1 to leave 0 for the index column
                newConfig.addColumnConfiguration(new ColumnConfiguration(
                        columnMap.size(),
                        newColumnName,
                        valuesConfiguration.getType(),
                        valuesConfiguration.getType().getDefaultScale(),
                        valuesConfiguration.getConfigurations()));
            }
            // Complete the pivotedData hashMap
            // TODO : add aggregation function ??
            pivotedData.computeIfAbsent(indexValue, k -> new HashMap<>()).put(newColumnName, value);
        }

        // Create new DataRows for the pivoted DataSet
        List<DataRow> newRows = new ArrayList<>();
        for (Map.Entry<Object, Map<Object, Object>> entry : pivotedData.entrySet()) {
            List<Data> newRowData = new ArrayList<>();
            newRowData.add(DataGeneration.createDataByTypeAndValue(indexConfiguration.getType(), entry.getKey()));  // Index column
            Map<Object, Object> cols = entry.getValue();
            // Ensure all columns are present in each row, fill with null if necessary
            // TODO : discuss default value
            for (int i = 1; i <= columnMap.size(); i++) {
                ColumnConfiguration colConfig = newConfig.getConfigurations().get(i);
                Object colValue = cols.get(colConfig.getName());
                newRowData.add(DataGeneration.createDataByTypeAndValue(colConfig.getType(), colValue));
            }
            newRows.add(new DataRow(newRowData));
        }

        return new DataSet(newRows, newConfig);
    }

    /**
     * Transforms a DataSet from wide to long format, where one or more columns are melted into two columns: 'variable' and 'value'.
     * Mimic pandas.melt() function.
     *
     * @param idVars An array of column names that will remain as identifier variables.
     * @param valueVars An array of column names that will be melted into two columns: one for the variable names and one for the values.
     * @return A new DataSet in long format.
     * @throws IllegalArgumentException If the original DataSet is null or if specified columns do not exist.
     *
     * This method creates a new configuration for the resulting DataSet. Identifier columns retain their original data type and order,
     * while 'variable' column is always of type STRING, and 'value' column dynamically assumes the data type of the original column being melted.
     * Each row in the resulting DataSet corresponds to a unique combination of id values and valueVar, with two additional columns:
     * 'variable' (name of the melted column) and 'value' (value from the melted column).
     */
    public DataSet melt(String[] idVars, String[] valueVars) {
        if (dataSet == null) {
            throw new IllegalArgumentException("DataSet cannot be null.");
        }

        // Validate that all idVars and valueVars exist in the dataSet
        for (String idVar : idVars) {
            validateColumnExistence(dataSet.getDataConfiguration(), idVar);
        }
        for (String valueVar : valueVars) {
            validateColumnExistence(dataSet.getDataConfiguration(), valueVars);
        }

        // Create new configuration
        DataConfiguration newConfig = new DataConfiguration();
        int configIndex = 0;
        // Add id columns
        for (String idVar : idVars) {
            DataType dataType = Objects.requireNonNull(dataSet.getDataConfiguration()
                    .getColumnConfigurationByColumnName(idVar)).getType();
            newConfig.addColumnConfiguration(new ColumnConfiguration(configIndex, idVar, dataType, dataType.getDefaultScale(), new ArrayList<>()));
            configIndex += 1;
        }
        // Prepare columns for 'variable' and 'value'
        newConfig.addColumnConfiguration(new ColumnConfiguration(configIndex, "variable", DataType.STRING, DataType.STRING.getDefaultScale(), new ArrayList<>()));
        newConfig.addColumnConfiguration(new ColumnConfiguration(configIndex + 1, "value", DataType.STRING, DataType.STRING.getDefaultScale(), new ArrayList<>()));

        List<DataRow> newRows = new ArrayList<>();

        for (DataRow row : dataSet.getDataRows()) {
            for (String valueVar : valueVars) {
                DataType valueType = Objects.requireNonNull(dataSet.getDataConfiguration()
                        .getColumnConfigurationByColumnName(valueVar)).getType();

                List<Data> newRowData = new ArrayList<>();

                // Add identifier column values
                for (String idVar : idVars) {
                    int idColumnIndex = Objects.requireNonNull(dataSet.getDataConfiguration()
                            .getColumnConfigurationByColumnName(idVar)).getIndex();
                    Data data = row.getData().get(idColumnIndex);
                    newRowData.add(data);
                }

                // Add 'variable' and 'value' columns values
                Data variableData = new StringData(valueVar);
                int valueColumnIndex = Objects.requireNonNull(dataSet.getDataConfiguration()
                        .getColumnConfigurationByColumnName(valueVar)).getIndex();
                Data valueData = row.getData().get(valueColumnIndex);

                newRowData.add(variableData);
                newRowData.add(valueData);

                newRows.add(new DataRow(newRowData));
            }
        }
        return new DataSet(newRows, newConfig);
    }



    /**
     * Transforms a DataSet from wide format to long format. Mimic pandas.wide_to_long() function.
     *
     * This method reshapes a DataSet containing multiple columns for different years or categories
     * into a long format where there is one row per year or category per subject.
     *
     * @param idVarName The name of the column that uniquely identifies each subject or entity
     *                  in the original DataSet. This column is preserved in the transformed DataSet.
     * @param stubnames A list of the column names that are to be transformed from wide to long format.
     *                  These names should correspond to different time periods, measurements, or categories.
     * @param i The name of the new column in the long format DataSet that will contain the stub names.
     * @param j The name of the new column in the long format DataSet that will contain the values
     *          corresponding to the stub names.
     *
     * @return A new DataSet in long format.
     *
     * @throws IllegalArgumentException If any of the specified stubnames do not exist in the original DataSet.
     *
     * Example Usage:
     * DataSet longDataSet = transformation.wideToLong("id", Arrays.asList("Year1970", "Year1980"), "Year", "Value");
     *
     * In the resulting DataSet, 'Year' column will have 'Year1970', 'Year1980' and 'Value' column will have
     * corresponding values to these years.
     */
    public DataSet wideToLong(String idVarName, List<String> stubnames, String i, String j) {
        assert dataSet != null;
        // TODO : check validity of parameters (existing attributes, same Data type for stubnames..)
        List<DataRow> longFormatDataRows = new ArrayList<>();
        DataConfiguration newConfig = new DataConfiguration();

        // Add configuration for id column
        newConfig.addColumnConfiguration(new ColumnConfiguration(0, idVarName, DataType.INTEGER, DataType.INTEGER.getDefaultScale(), new ArrayList<>()));
        // Add configuration for stub column
        newConfig.addColumnConfiguration(new ColumnConfiguration(1, i, DataType.STRING, DataType.STRING.getDefaultScale(), new ArrayList<>()));
        // Add configuration for value column
        // TODO : dynamically determine dataType and scale
        DataType valueDataType = dataSet.getDataConfiguration().getColumnConfigurationByColumnName(stubnames.get(0)).getType();
        newConfig.addColumnConfiguration(new ColumnConfiguration(2, j, valueDataType, valueDataType.getDefaultScale(), new ArrayList<>()));

        //TODO : modify index in others columns config
        // Obtenir les indices et les types des colonnes dans le dataset original
        Map<String, Integer> columnIndexMap = new HashMap<>();
        Map<String, DataType> columnTypeMap = new HashMap<>();

        dataSet.getDataConfiguration().getConfigurations().forEach(config -> {
            columnIndexMap.put(config.getName(), config.getIndex());
            columnTypeMap.put(config.getName(), config.getType());
        });
        // Transform each DataRow
        for (DataRow row : dataSet.getDataRows()) {
            List<Data> rowData = row.getData();

            for (String stub : stubnames) {
                // Check if stub is in map
                if (!columnIndexMap.containsKey(stub)) {
                    continue;  // TODO: handle error
                }

                Data idData = DataGeneration.createDataByType(columnTypeMap.get(idVarName), rowData.get(columnIndexMap.get(idVarName)).getValue());
                Data stubData = DataGeneration.createDataByType(columnTypeMap.get(stub), rowData.get(columnIndexMap.get(stub)).getValue());

                // Create new DataRow
                List<Data> newRowData = Arrays.asList(idData, new StringData(stub), stubData);
                longFormatDataRows.add(new DataRow(newRowData));
            }
        }
        return new DataSet(longFormatDataRows, newConfig);
    }

    /**
     * Merges multiple datasets into a single dataset based on specified join conditions and columns.
     * This method iteratively merges datasets starting from the first in the list, using a specified join
     * strategy ('how'), and columns to join on ('on'). The result is a single merged dataset accompanied
     * by traceability information for each merge operation.
     *
     * @param on A list of column names used for joining datasets. Each element corresponds to a column
     *           in the respective dataset in the dataSets list. The size of 'on' must match the size
     *           of 'dataSets'.
     * @param how Specifies the type of join to perform ('inner', 'outer', 'left', 'right').
     * @return MergedDataSetTraceabilityList containing the final merged dataset and split information for
     *         each dataset involved in the merge.
     * @throws IllegalArgumentException If 'dataSets' is null or the size of 'on' does not match the size of 'dataSets'
     * @throws IllegalArgumentException of mergeLeftAndRightDatasets method.
     */

    public MergedDataSetTraceabilityList mergeDataSets(List<String> on, String how) {
        if (dataSets == null) {
            throw new IllegalArgumentException("Please provide DataSetList for merging.");
        }
        if (dataSets.size() != on.size()) {
            throw new IllegalArgumentException("DataSetsList and on list have different size.");
        }
        List<DataSetSplittingInformation> dataSetSplittingInformationList = new ArrayList<>();
        DataSet leftDataSet = dataSets.get(0);
        for (int i = 1; i < dataSets.size(); i++) {

            DataSet rightDataSet = dataSets.get(i);
            MergedDataSetTraceability mergedDataSetTraceability = mergeLeftAndRightDatasets(
                    leftDataSet, rightDataSet, on.get(0), on.get(i), how );
            DataConfiguration rightConfigurationSplittingInfo = mergedDataSetTraceability.
                    getRightDataSetSplittingInformation().getDataConfiguration();
            DataConfiguration leftConfigurationSplittingInfo = mergedDataSetTraceability.
                    getLeftDataSetSplittingInformation().getDataConfiguration();
            leftDataSet = mergedDataSetTraceability.getMergedDataSet();
            // keep the first left dataSetSplittingInformation
            if (i==1) {
                dataSetSplittingInformationList.add(mergedDataSetTraceability.getLeftDataSetSplittingInformation());
            }
            dataSetSplittingInformationList.add(mergedDataSetTraceability.getRightDataSetSplittingInformation());
        }
        return new MergedDataSetTraceabilityList(leftDataSet, dataSetSplittingInformationList);
    }

    /**
     * Splits a merged dataset into subsets according to specified indices and configurations.
     * Each subset corresponds to original datasets as defined by DataSetSplittingInformation which includes
     * index information and data configurations specific to each original dataset.
     * Some information might be lost according to the method used for merging datasets.
     * Append to datasets each DataSet that represents a subset of the original datasets reconstructed
     * from the merged dataset according to the provided split information.
     *
     * @param mergedDataSetTraceabilityList Contains the merged dataset along with splitting information detailing
     *                                      how to reconstruct subsets from the merged dataset.
     * @param mergedDataSetIndexColumnIndex The column index in the merged dataset rows that contains the index key
     *                                      used to determine to which subset a row belongs.
     */
    public void splitMergedDataset(MergedDataSetTraceabilityList mergedDataSetTraceabilityList, int mergedDataSetIndexColumnIndex) {
        List<DataSet> subsets = new ArrayList<>();
        DataSet mergedDataSet = mergedDataSetTraceabilityList.getMergedDataSet();

        // Create an empty DataSet for each splitting information set
        for (DataSetSplittingInformation info : mergedDataSetTraceabilityList.getDataSetSplittingInformationList()) {
            subsets.add(new DataSet(new ArrayList<>(), info.getDataConfiguration()));
        }

        // Map of indexes to their corresponding rows in the merged dataset
        Map<Data, DataRow> indexToRowMap = new HashMap<>();
        for (DataRow row : mergedDataSet.getDataRows()) {
            Data indexData = row.getData().get(mergedDataSetIndexColumnIndex);
            indexToRowMap.put(indexData, row);
        }

        // Populate each subset based on the indexes
        for (int i = 0; i < mergedDataSetTraceabilityList.getDataSetSplittingInformationList().size(); i++) {
            DataSetSplittingInformation splittingInfo = mergedDataSetTraceabilityList.getDataSetSplittingInformationList().get(i);
            DataSet subset = subsets.get(i);

            for (Data index : splittingInfo.getIndexes()) {
                DataRow correspondingRow = indexToRowMap.get(index);
                if (correspondingRow != null) {
                    List<Data> filteredRowData = new ArrayList<>();

                    for (String columnName : splittingInfo.getDataConfiguration().getColumnNames()) {
                        // Find matching column in mergedDataSet
                        int matchinColumnIndex = mergedDataSet.getDataConfiguration().getColumnConfigurationByColumnName(columnName).getIndex();
                        filteredRowData.add(correspondingRow.getData().get(matchinColumnIndex));
                    }
                    subset.getDataRows().add(new DataRow(filteredRowData));
                }
            }
            dataSets.add(subset);
        }
    }

    /**
     * Splits a single dataset into a specified number of equally sized subsets.
     *
     * @param dataset The dataset to split.
     * @param numberOfSplits The number of subsets to create from the dataset.
     * @return A list of DataSets, each representing a subset of the original dataset. Each subset
     *         contains approximately 'dataset.getDataRows().size() / numberOfSplits' rows, except possibly
     *         the last subset which may contain additional rows if the total number of rows is not evenly divisible
     *         by 'numberOfSplits'.
     */
    public List<DataSet> splitDataset(DataSet dataset, int numberOfSplits) {
        List<DataSet> subsets = new ArrayList<>();
        int splitSize = dataset.getDataRows().size() / numberOfSplits;
        for (int i = 0; i < numberOfSplits; i++) {
            int start = i * splitSize;
            int end = (i + 1 == numberOfSplits) ? dataset.getDataRows().size() : (i + 1) * splitSize;
            List<DataRow> subset = dataset.getDataRows().subList(start, end);
            subsets.add(new DataSet(subset, dataset.getDataConfiguration()));
        }
        return subsets;
    }

    /**
     * Append all datasets stored in this instance horizontally, ensuring their data configurations are identical.
     *
     * @return A new dataset that is the result of appending all datasets horizontally.
     * @throws IllegalArgumentException if any of the data configurations are not the same.
     */
    public DataSet appendDatasetsHorizontally() {
        if (dataSets == null || dataSets.size() < 2) {
            throw new IllegalArgumentException("There must be at least two datasets to append.");
        }

        DataSet baseDataSet = dataSets.get(0);
        List<DataRow> combinedRows = new ArrayList<>(baseDataSet.getDataRows());

        for (int i = 1; i < dataSets.size(); i++) {
            DataSet currentDataSet = dataSets.get(i);
            if (!baseDataSet.getDataConfiguration().equals(currentDataSet.getDataConfiguration())) {
                throw new IllegalArgumentException("Cannot append datasets with different configurations.");
            }
            combinedRows.addAll(currentDataSet.getDataRows());
        }

        return new DataSet(combinedRows, baseDataSet.getDataConfiguration());
    }

    /**
     * Append all datasets stored in this instance vertically, ensuring the id column as identical values.
     *
     * @return A new dataset that is the result of appending all datasets vertically.
     * @throws IllegalArgumentException - if the id columns are not compatible
     *                                  - if two columns with the same name are included TODO : Or change the column name ?
     */
    public DataSet appendDatasetsVertically(DataSet ds1, DataSet ds2, String indexColumnName) {
//        TODO: what difference with mergeDataset ?
        return ds1;
    }
}
