package de.kiaim.cinnamon.anonymization.helper;

import de.kiaim.cinnamon.anonymization.model.dataSetTransformation.DataSetSplittingInformation;
import de.kiaim.cinnamon.anonymization.model.dataSetTransformation.MergedDataSetTraceability;
import de.kiaim.cinnamon.anonymization.service.CompatibilityAssurance;
import de.kiaim.cinnamon.model.configuration.data.ColumnConfiguration;
import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.data.Data;
import de.kiaim.cinnamon.model.data.DataRow;
import de.kiaim.cinnamon.model.data.DataSet;

import java.util.*;

/**
 * Class with methods to build the merge dataset method in DatasetTransformation
 * */
public class MergeDataSets {

    /**
     * Merges two datasets based on specified key columns and merge type.
     *
     * @param leftDataSet The other dataset to merge with this dataset.
     *                    The right dataSet is the one declare in the constructor.
     * @param leftOn Left dataset column name used to perform the merge (the column configuration must be compatible).
     * @param rightOn Right dataset column name used to perform the merge (the column configuration must be compatible).
     * @param how Type of merge to perform ('inner', 'outer', 'left', 'right').
     * @return A new DataSet resulting from the merge.
     * @throws IllegalArgumentException If the index column names are not present in dataset
     *          or if index column specified comports duplicates
     *          or if columns for join are not compatible
     *          or if an unsupported merge type is specified
     *          or if two column of each dataset have the same name but not the same columnConfiguration
     */
    public static MergedDataSetTraceability mergeLeftAndRightDatasets(DataSet leftDataSet, DataSet rightDataSet, String leftOn, String rightOn, String how) {

        // Validate columns exist in both datasets
        if (leftDataSet.getDataConfiguration().getColumnConfigurationByColumnName(leftOn) == null) {
            throw new IllegalArgumentException("Index column does not exist in leftDataSet: " + leftOn);
        }
        else if (rightDataSet.getDataConfiguration().getColumnConfigurationByColumnName(rightOn) == null) {
            throw new IllegalArgumentException("Index column does not exist in rightDataSet: " + rightOn);
        }

        // Validate compatibility between both index columns ( allow different names only)
        CompatibilityAssurance.checkColumnsCompatibility(Objects.requireNonNull(leftDataSet.getDataConfiguration().getColumnConfigurationByColumnName(leftOn)),
                Objects.requireNonNull(rightDataSet.getDataConfiguration().getColumnConfigurationByColumnName(rightOn)));

        // Validate that specified right and left index columns are id columns (no duplicate values)
        validateIsIndexColumn(leftDataSet, leftDataSet.getDataConfiguration().getColumnConfigurationByColumnName(leftOn));
        validateIsIndexColumn(rightDataSet, rightDataSet.getDataConfiguration().getColumnConfigurationByColumnName(rightOn));

        // Generate new config
        // and build original dataset info object : column config for index and index values
        MergedDataSetTraceability temporaryMergedDataSet = generateMergedConfiguration(leftDataSet.getDataConfiguration(), rightDataSet.getDataConfiguration(), leftOn, rightOn);

        DataConfiguration newConfig = temporaryMergedDataSet.getMergedDataSet().getDataConfiguration();

        Set<Data> leftIndexes = extractIndexes(leftDataSet, leftOn);
        Set<Data> rightIndexes = extractIndexes(rightDataSet, rightOn);

        // Generate merged indexes list
        List<Data> mergedIndexes = indexesList(leftIndexes, rightIndexes, how);
        Map<Data, List<Data>> tempStorage = new HashMap<>();

        // Initialise the structure with empty lists for all mergedIndexes
        for (Data index : mergedIndexes) {
            // initialize row for all the columns but the new index
            List<Data> emptyRow = new ArrayList<>(Collections.nCopies(newConfig.getConfigurations().size()-1, null));
            tempStorage.put(index, emptyRow);
        }

        // Fill tempStorage with DataSets values
        // TODO : handle null values
        fillMergedDataSet(leftDataSet, leftOn, tempStorage, newConfig);
        fillMergedDataSet(rightDataSet, rightOn, tempStorage, newConfig);

        // Create new DataSet from tempStorage
        DataSet mergedDataset = createMergedDataSet(tempStorage, newConfig);
        DataSetSplittingInformation leftDataSetSplittingInfo = new DataSetSplittingInformation(leftDataSet.getDataConfiguration(), leftIndexes);
        DataSetSplittingInformation rightDataSetSplittingInfo = new DataSetSplittingInformation(rightDataSet.getDataConfiguration(), rightIndexes);

        return new MergedDataSetTraceability(mergedDataset, leftDataSetSplittingInfo, rightDataSetSplittingInfo);
    }

    /**
     * Validates whether a given column in a DataSet can be considered as an index column.
     * An index column should have unique values for each DataRow within the DataSet.
     *
     * @param dataSet The DataSet to be checked for index validity.
     * @param columnConfiguration The configuration of the column which is being validated as an index.
     * @throws IllegalArgumentException if any duplicate values are found in the specified column,
     *                                  implying that the column cannot serve as a unique index.
     * @throws IndexOutOfBoundsException if the columnIndex specified in the columnConfiguration
     *                                   is out of bounds for the data rows in the dataSet.
     */
    public static void validateIsIndexColumn(DataSet dataSet, ColumnConfiguration columnConfiguration){
        List<Data> indexes = new ArrayList<>();
        for (DataRow row : dataSet.getDataRows()) {
            int columnIndex = columnConfiguration.getIndex();
            Data rowIndex = row.getData().get(columnIndex);
            if (indexes.contains(rowIndex)) {
                throw new IllegalArgumentException("Column " + columnConfiguration.getName() + " contains duplicates : not an index.");
            } else {
                indexes.add(rowIndex);
            }
        }
    }

    /**
     * Generate new DataConfiguration for merged DataSet and save the initial DataSet information for splitting.
     *
     * @param rightDataConfiguration  The DataType of the data to be created.
     * @param leftDataConfiguration The value for the Data object, must be of a type compatible with the DataType.
     * @return A new instance of a subclass of Data corresponding to the specified DataType.
     * @throws IllegalArgumentException If two columns are found with the same name.
     */
    public static MergedDataSetTraceability generateMergedConfiguration(DataConfiguration leftDataConfiguration, DataConfiguration rightDataConfiguration, String leftOn, String rightOn){
        DataConfiguration newConfig = new DataConfiguration();
        // Save information on orginal datasets for splitting
        DataConfiguration leftOriginalColumnsConfiguration = new DataConfiguration();
        DataConfiguration rightOriginalColumnsConfiguration = new DataConfiguration();


        ColumnConfiguration indexConfig = leftDataConfiguration.getColumnConfigurationByColumnName(leftOn);
        assert indexConfig != null;
        ColumnConfiguration newIndexConfig = new ColumnConfiguration(0, indexConfig.getName(), indexConfig.getType(), indexConfig.getScale(), indexConfig.getConfigurations());
        newConfig.addColumnConfiguration(newIndexConfig);
        leftOriginalColumnsConfiguration.addColumnConfiguration(newIndexConfig);
        rightOriginalColumnsConfiguration.addColumnConfiguration(newIndexConfig);

        int i = 1;
        int j = 1;

        // Add leftDataConfiguration columns with new index
        for (ColumnConfiguration  leftColumnConfig : leftDataConfiguration.getConfigurations()){
            if (!Objects.equals(leftColumnConfig.getName(), rightOn)) {
                ColumnConfiguration newColumnConfig = new ColumnConfiguration(
                        i,
                        leftColumnConfig.getName(),
                        leftColumnConfig.getType(),
                        leftColumnConfig.getScale(),
                        leftColumnConfig.getConfigurations());
                newConfig.addColumnConfiguration(newColumnConfig);
                leftOriginalColumnsConfiguration.addColumnConfiguration(newColumnConfig);
                i += 1;
            }
        }

        // Add rightDataConfiguration columns with new index
        for (ColumnConfiguration rightColumnConfig : rightDataConfiguration.getConfigurations()){
            if (!Objects.equals(rightColumnConfig.getName(), leftOn)) {
                if (!newConfig.getColumnNames().contains(rightColumnConfig.getName())) {
                    newConfig.addColumnConfiguration(
                            new ColumnConfiguration(
                                    i,
                                    rightColumnConfig.getName(),
                                    rightColumnConfig.getType(),
                                    rightColumnConfig.getScale(),
                                    rightColumnConfig.getConfigurations()));
                    rightOriginalColumnsConfiguration.addColumnConfiguration(
                            new ColumnConfiguration(
                                    j,
                                    rightColumnConfig.getName(),
                                    rightColumnConfig.getType(),
                                    rightColumnConfig.getScale(),
                                    rightColumnConfig.getConfigurations()));
                    i += 1;
                    j += 1;
                }
                // If one column in rightConfiguration has an already existing name but not the same type or scale or config, raise error
                else if (newConfig.getColumnNames().contains(rightColumnConfig.getName())) {
                    // Take the similar config in the left dataSet
                    ColumnConfiguration similarRightColumnConfig = leftDataConfiguration.getColumnConfigurationByColumnName(rightColumnConfig.getName());
                    if ( similarRightColumnConfig.getType() != rightColumnConfig.getType()
                    || !similarRightColumnConfig.getConfigurations().equals(rightColumnConfig.getConfigurations())
                    || similarRightColumnConfig.getScale() != rightColumnConfig.getScale()) {
                        throw new IllegalArgumentException("Two non-identical columns have the same name. ");
                    }
                }
            }
        }

        DataSetSplittingInformation leftDataSetSplittingInformation = new DataSetSplittingInformation(leftOriginalColumnsConfiguration, new HashSet<>());
        DataSetSplittingInformation rightDataSetSplittingInformation = new DataSetSplittingInformation(rightOriginalColumnsConfiguration, new HashSet<>());
        DataSet mergedDataSet = new DataSet(new ArrayList<>(), newConfig);
        return new MergedDataSetTraceability(mergedDataSet, leftDataSetSplittingInformation, rightDataSetSplittingInformation);
    }

    /**
     * Generate indexes list for merging DataSet depending on how method.
     *
     * @param rightIndexes Right dataset indexes.
     * @param leftIndexes Left dataset indexes.
     * @param how Type of merge to perform ('inner', 'outer', 'left', 'right').
     * @return A list of Data that represent the indexes for the merged dataset.
     * @throws IllegalArgumentException If how method unsupported.
     */
    public static List<Data> indexesList(Set<Data> leftIndexes, Set<Data> rightIndexes, String how){
        Set<Data> resultIndexes = new HashSet<>();

        switch (how) {
            case "inner":
                resultIndexes = rightIndexes;
                resultIndexes.retainAll(leftIndexes);
                break;
            case "left":
                resultIndexes = leftIndexes;
                break;
            case "right":
                resultIndexes = rightIndexes;
                break;
            case "outer":
                resultIndexes.addAll(rightIndexes);
                resultIndexes.addAll(leftIndexes);
                break;
            default:
                throw new IllegalArgumentException("Unsupported data type: " + how);
        }

        return new ArrayList<>(resultIndexes);

    }
    /**
     * Generate Set object with dataset indexes to make comparison easier.
     *
     * @param dataSet dataset
     * @param indexColumnName Name of the index column
     */
    public static Set<Data> extractIndexes(DataSet dataSet, String indexColumnName) {
        Set<Data> indexes = new HashSet<>();
        int indexColumnIndex = dataSet.getDataConfiguration().getColumnConfigurationByColumnName(indexColumnName).getIndex();
        for (DataRow row : dataSet.getDataRows()) {
            Data indexData = row.getData().get(indexColumnIndex);
            indexes.add(indexData);
        }
        return indexes;
    }

    /**
     * Populates a merged dataset from a given dataset based on a specified index column.
     * This function iterates over each DataRow in the given DataSet, retrieves the index data,
     * and uses it to update a storage map that accumulates merged data rows according to a new configuration.
     *
     * @param dataSet The DataSet from which data rows are sourced.
     * @param indexColumnName The name of the column used as an index to identify corresponding rows
     *                        across different data configurations.
     * @param storage A map linking index Data objects to lists of Data, representing merged rows.
     *                This map is used to accumulate and update rows during the merging process.
     * @param newConfig The new data configuration that defines how the rows should be structured
     *                  in the merged dataset. This configuration dictates the structure and mapping
     *                  of data columns from the original dataset configurations.
     */
    public static void fillMergedDataSet(DataSet dataSet, String indexColumnName, Map<Data, List<Data>> storage, DataConfiguration newConfig) {
        int indexColumnIndex = Objects.requireNonNull(dataSet.getDataConfiguration().getColumnConfigurationByColumnName(indexColumnName)).getIndex();
        for (DataRow row : dataSet.getDataRows()) {
            Data indexData = row.getData().get(indexColumnIndex);
            // if key in storage fill with dataset values matching new configuration with datasetConfiguration
            if (storage.containsKey(indexData)) {
                List<Data> mergedRowData = storage.get(indexData);

                updateRowData(mergedRowData, row, dataSet.getDataConfiguration(), indexColumnName, newConfig);
            }
        }
    }

    /**
     * Updates the row data for a merged dataset, ensuring that each data point from the original
     * dataset's row is correctly placed according to the new configuration.
     *
     * @param mergedRowData The list of Data objects representing the merged row, to be updated.
     * @param row The current DataRow from the original dataset being processed.
     * @param datasetConfig The original dataset's configuration, used to find corresponding columns.
     * @param indexColumnName The name of the index column, which is skipped during the update.
     * @param newConfig The new data configuration that provides the structure and mapping for the merged row.
     */
    private static void updateRowData(List<Data> mergedRowData, DataRow row, DataConfiguration datasetConfig, String indexColumnName, DataConfiguration newConfig) {
        for (ColumnConfiguration mergedColumnConfig : newConfig.getConfigurations()) {
            // Skip the index column
            if (!Objects.equals(mergedColumnConfig.getName(), indexColumnName)) {
                ColumnConfiguration matchingColumn = datasetConfig.getColumnConfigurationByColumnName(mergedColumnConfig.getName());
                if (matchingColumn != null) {
                    int matchingColumnIndex = matchingColumn.getIndex();

                    // Ensure that the index is within the bounds of the row's data
                    if (matchingColumnIndex < row.getData().size()) {
                        int mergedIndex = mergedColumnConfig.getIndex() - 1; // Adjust for missing index column
                        // Ensure that the adjusted index is within bounds of mergedRowData
                        if (mergedIndex >= 0 && mergedIndex < mergedRowData.size()) {
                            mergedRowData.set(mergedIndex, row.getData().get(matchingColumnIndex));
                        } else {
                            // Handle or log the situation where the index is out of bounds
                            System.out.println("Index out of bounds: " + mergedIndex);
                        }
                    }
                }
            }
        }
    }

    /**
     * Creates a merged DataSet from a storage map that contains index keys and corresponding list of Data objects.
     * Each entry in the storage map represents a DataRow in the merged DataSet, with the List<Data> as the row data.
     *
     * @param storage A Map with keys of type Data representing the indexes and values of type List<Data> representing
     *                the row data for each index.
     * @param newConfig The new DataConfiguration that describes the column structure and other configurations for
     *                  the new DataSet.
     * @return A DataSet containing the merged data, structured according to the new DataConfiguration.
     */
    public static DataSet createMergedDataSet(Map<Data, List<Data>> storage, DataConfiguration newConfig) {
        List<DataRow> rows = new ArrayList<>();
        for (Map.Entry<Data, List<Data>> entry : storage.entrySet()) {
            List<Data> rowData = new ArrayList<>();
            rowData.add(entry.getKey());  // Add key as index value
            rowData.addAll(entry.getValue());  // Add other values
            rows.add(new DataRow(rowData));  // Create new DataRow
        }
        return new DataSet(rows, newConfig);
    }
}
