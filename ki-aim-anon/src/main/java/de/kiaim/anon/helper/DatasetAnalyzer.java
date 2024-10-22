package de.kiaim.anon.helper;

import de.kiaim.model.data.Data;
import de.kiaim.model.data.DataRow;
import de.kiaim.model.data.DataSet;

import java.util.List;

/**
 * This class is to generate the JAL anonymization attributes parameters that need to be
 * generated from the Dataset itself.
 * TODO : moove to DataSet statistics of KI-AIM PLATFORM module ?
 */
public class DatasetAnalyzer {

    /**
     * Finds the minimum and maximum values for a given column index in a dataset.
     * This method assumes that the column contains either DECIMAL or INTEGER values.
     *
     * @param dataSet The dataset containing the data rows.
     * @param columnIndex The index of the column for which to find min and max values.
     * @return An array containing two elements: [min, max].
     *         If the column is empty or contains no valid numerical data, it returns [null, null].
     */
    public static Number[] findMinMaxForColumn(DataSet dataSet, int columnIndex) {
        List<DataRow> rows = dataSet.getDataRows();

        Number min = null;
        Number max = null;

        for (DataRow row : rows) {
            // Check if the row has enough columns
            if (row.getRow().size() > columnIndex) {
                Data data = row.getData().get(columnIndex);
                Object value = data.getValue();

                if (value instanceof Number) {
                    Number numberValue = (Number) value;

                    if (min == null || numberValue.doubleValue() < min.doubleValue()) {
                        min = numberValue;
                    }

                    if (max == null || numberValue.doubleValue() > max.doubleValue()) {
                        max = numberValue;
                    }
                }
            }
        }

        return new Number[]{min, max};
    }
}
