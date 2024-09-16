package de.kiaim.anon.helper;

import de.kiaim.model.configuration.data.ColumnConfiguration;
import de.kiaim.model.data.Data;
import de.kiaim.model.data.DataRow;
import de.kiaim.model.data.DataSet;

import java.util.List;

public class PrintDataset {

    /**
     * Prints the given DataSet to the console in a tabular format.
     * This method formats the output to align data under their respective column headers.
     *
     * @param dataSet The DataSet to be printed.
     */
    public static void printDataSet(DataSet dataSet) {
        // Get config to display columns name
        List<ColumnConfiguration> configurations = dataSet.getDataConfiguration().getConfigurations();
        // Create String header
        StringBuilder header = new StringBuilder();
        for (ColumnConfiguration config : configurations) {
            header.append(String.format("%-20s", config.getName()));
        }
        System.out.println(header.toString());

        // Add rows as string
        for (DataRow row : dataSet.getDataRows()) {
            StringBuilder rowString = new StringBuilder();
            for (Data data : row.getData()) {
                if (data != null) {
                    rowString.append(String.format("%-20s", data.getValue().toString()));
                } else {
                    rowString.append(String.format("%-20s", ""));
                }
            }
            System.out.println(rowString.toString());
        }
    }
}
