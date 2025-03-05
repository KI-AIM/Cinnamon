package util;

import de.kiaim.model.configuration.data.ColumnConfiguration;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.data.*;
import de.kiaim.model.enumeration.DataScale;
import de.kiaim.model.enumeration.DataType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class GenerateTestDatasets {
    /**
     * Generates a predefined data configuration for testing purposes.
     * This configuration includes a variety of data types and scales.
     *
     * @return A DataConfiguration object populated with predefined column configurations.
     */
    public static DataConfiguration generateEstimatedConfiguration() {
        final DataConfiguration configuration = new DataConfiguration();
        final List<ColumnConfiguration> columnConfigurations = List.of(
                new ColumnConfiguration(0, "column0_boolean", DataType.BOOLEAN, DataScale.NOMINAL ,new ArrayList<>()),
                new ColumnConfiguration(1, "column1_date", DataType.DATE, DataScale.DATE, new ArrayList<>()),
                new ColumnConfiguration(2, "column2_date_time", DataType.DATE_TIME, DataScale.DATE, new ArrayList<>()),
                new ColumnConfiguration(3, "column3_decimal", DataType.DECIMAL, DataScale.RATIO, new ArrayList<>()),
                new ColumnConfiguration(4, "column4_integer", DataType.INTEGER, DataScale.INTERVAL, new ArrayList<>()),
                new ColumnConfiguration(5, "column5_string", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()));
        configuration.setConfigurations(columnConfigurations);

        return configuration;
    }

    /**
     * Creates a list of DataRow objects containing sample data.
     * Each DataRow includes data of various types (boolean, date, date_time, decimal, integer, string).
     *
     * @return A List of DataRow objects filled with sample data for testing.
     */
    public static List<DataRow> generateDataRows() {
        final List<Data> data1 = List.of(new BooleanData(true),
                new DateData(LocalDate.of(2023, 11, 20)),
                new DateTimeData(LocalDateTime.of(2023, 11, 20, 12, 50, 27, 123456000)),
                new DecimalData(4.2f),
                new IntegerData(42),
                new StringData("Hello World!"));
        final List<Data> data2 = List.of(new BooleanData(false),
                new DateData(LocalDate.of(2023, 11, 20)),
                new DateTimeData(LocalDateTime.of(2023, 11, 20, 12, 50, 27, 123456000)),
                new DecimalData(2.4f),
                new IntegerData(24),
                new StringData("Bye World!"));
        final DataRow dataRow1 = new DataRow(data1);
        final DataRow dataRow2 = new DataRow(data2);
        return List.of(dataRow1, dataRow2);
    }

    /**
     * Generates an alternative data configuration for testing,
     * where column order is different from generateEstimatedConfiguration to test data consistency and column index dependencies.
     *
     * @return A DataConfiguration object with columns in a different order than the first configuration method.
     */
    public static DataConfiguration generateEstimatedConfiguration2() {
        final DataConfiguration configuration = new DataConfiguration();
        final List<ColumnConfiguration> columnConfigurations = List.of(
                new ColumnConfiguration(0, "column1_date", DataType.DATE, DataScale.DATE, new ArrayList<>()),
                new ColumnConfiguration(1, "column0_boolean", DataType.BOOLEAN, DataScale.NOMINAL ,new ArrayList<>()),
                new ColumnConfiguration(2, "column2_date_time", DataType.DATE_TIME, DataScale.DATE, new ArrayList<>()),
                new ColumnConfiguration(3, "column3_decimal", DataType.DECIMAL, DataScale.RATIO, new ArrayList<>()),
                new ColumnConfiguration(4, "column4_integer", DataType.INTEGER, DataScale.INTERVAL, new ArrayList<>()),
                new ColumnConfiguration(5, "column5_string", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()));
        configuration.setConfigurations(columnConfigurations);

        return configuration;
    }
    /**
     * Creates an alternative list of DataRow objects with sample data,
     * structured differently from generateDataRows to test handling of varying data arrangements.
     *
     * @return A List of DataRow objects where data column order is shuffled.
     */
    public static List<DataRow> generateDataRows2() {
        final List<Data> data1 = List.of(new DateData(LocalDate.of(2023, 11, 20)),
                new BooleanData(true),
                new DateTimeData(LocalDateTime.of(2022, 11, 20, 12, 50, 27, 123456000)),
                new DecimalData(4.2f),
                new IntegerData(42),
                new StringData("Hello World!"));
        final List<Data> data2 = List.of(new DateData(LocalDate.of(2023, 11, 20)),
                new BooleanData(false),
                new DateTimeData(LocalDateTime.of(2022, 11, 20, 12, 50, 27, 123456000)),
                new DecimalData(2.4f),
                new IntegerData(24),
                new StringData("Bye World!"));
        final DataRow dataRow1 = new DataRow(data1);
        final DataRow dataRow2 = new DataRow(data2);
        return List.of(dataRow1, dataRow2);
    }

    /**
     * Constructs a DataSet using the first set of data rows and configuration.
     * This method combines predefined rows and configurations for a complete dataset.
     *
     * @return A DataSet constructed from predefined data rows and configuration.
     */
    public static DataSet generateDataSetWithConfig() {
        return new DataSet(generateDataRows(), generateEstimatedConfiguration());
    }

    /**
     * Constructs a DataSet using the second set of data rows and alternative configuration.
     * Designed to test the system's ability to handle different data configurations and row setups.
     *
     * @return A DataSet constructed from the second set of predefined data rows and configuration.
     */
    public static DataSet generateDataSetWithConfig2() {
        return new DataSet(generateDataRows2(), generateEstimatedConfiguration2());
    }

    /**
     * Generates a wide-format DataSet designed for specific transformation tests.
     * The dataset includes identifiers and two years of data, for testing wide to long transformations.
     *
     * @return A wide-format DataSet with identifiers and year-specific data columns.
     */
    public static DataSet generateWideDataset() {
        List<DataRow> dataRows = new ArrayList<>();
        DataConfiguration config = new DataConfiguration();

        // Simulate the data configuration
        config.addColumnConfiguration(new ColumnConfiguration(0, "id", DataType.INTEGER, DataType.INTEGER.getDefaultScale(), new ArrayList<>()));
        config.addColumnConfiguration(new ColumnConfiguration(1, "Year1970", DataType.STRING, DataType.STRING.getDefaultScale(), new ArrayList<>()));
        config.addColumnConfiguration(new ColumnConfiguration(2, "Year1980", DataType.STRING, DataType.STRING.getDefaultScale(), new ArrayList<>()));

        // Simulate some data rows
        List<Data> row1Data = List.of(new IntegerData(1), new StringData("Data1970_1"), new StringData("Data1980_1"));
        List<Data> row2Data = List.of(new IntegerData(2), new StringData("Data1970_2"), new StringData("Data1980_2"));
        dataRows.add(new DataRow(row1Data));
        dataRows.add(new DataRow(row2Data));

        return new DataSet(dataRows, config);
    }

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

    /**
     * Generates a complex DataSet intended for testing pivot operations.
     * This DataSet includes multiple dimensions such as CustomerID, Year, Product, Category, AmountSpent, and Region.
     *
     * @return A complex DataSet with multiple attributes per DataRow, suitable for pivot tests.
     */
    public static DataSet createComplexTestDataSetForPivot() {
        // Data configuration
        DataConfiguration config = new DataConfiguration();
        config.addColumnConfiguration(new ColumnConfiguration(0, "CustomerID", DataType.INTEGER, null, new ArrayList<>()));
        config.addColumnConfiguration(new ColumnConfiguration(1, "Year", DataType.STRING, null, new ArrayList<>()));
        config.addColumnConfiguration(new ColumnConfiguration(2, "Product", DataType.STRING, null, new ArrayList<>()));
        config.addColumnConfiguration(new ColumnConfiguration(3, "Category", DataType.STRING, null, new ArrayList<>()));
        config.addColumnConfiguration(new ColumnConfiguration(4, "AmountSpent", DataType.DECIMAL, null, new ArrayList<>()));
        config.addColumnConfiguration(new ColumnConfiguration(5, "Region", DataType.STRING, null, new ArrayList<>()));

        // Create rows
        List<DataRow> rows = new ArrayList<>();
        rows.add(new DataRow(List.of(new IntegerData(1), new StringData("1970"), new StringData("Book"), new StringData("Education"), new DecimalData(150.00f), new StringData("North"))));
        rows.add(new DataRow(List.of(new IntegerData(1), new StringData("1980"), new StringData("Laptop"), new StringData("Electronics"), new DecimalData(1200.0f), new StringData("South"))));
        rows.add(new DataRow(List.of(new IntegerData(2), new StringData("1970"), new StringData("Pen"), new StringData("Stationary"), new DecimalData(15.0f), new StringData("West"))));
        rows.add(new DataRow(List.of(new IntegerData(2), new StringData("1980"), new StringData("Notebook"), new StringData("Education"), new DecimalData(35.0f), new StringData("East"))));
        rows.add(new DataRow(List.of(new IntegerData(3), new StringData("1970"), new StringData("Chair"), new StringData("Furniture"), new DecimalData(200.0f), new StringData("North"))));

        // Complete Dataset
        return new DataSet(rows, config);
    }

    public static DataSet createPandasExampleLongDataset() {
        DataConfiguration config = new DataConfiguration();
        config.addColumnConfiguration(new ColumnConfiguration(0, "foo", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()));
        config.addColumnConfiguration(new ColumnConfiguration(1, "bar", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()));
        config.addColumnConfiguration(new ColumnConfiguration(2, "baz", DataType.INTEGER, DataScale.INTERVAL, new ArrayList<>()));
        config.addColumnConfiguration(new ColumnConfiguration(3, "zoo", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()));

        List<DataRow> rows = new ArrayList<>();
        rows.add(new DataRow(List.of(new StringData("one"), new StringData("A"), new IntegerData(1), new StringData("x"))));
        rows.add(new DataRow(List.of(new StringData("one"), new StringData("A"), new IntegerData(2), new StringData("y"))));
        rows.add(new DataRow(List.of(new StringData("one"), new StringData("C"), new IntegerData(3), new StringData("z"))));
        rows.add(new DataRow(List.of(new StringData("two"), new StringData("A"), new IntegerData(4), new StringData("q"))));
        rows.add(new DataRow(List.of(new StringData("two"), new StringData("B"), new IntegerData(5), new StringData("w"))));
        rows.add(new DataRow(List.of(new StringData("two"), new StringData("C"), new IntegerData(6), new StringData("t"))));

        return new DataSet(rows, config);
    }

    public static DataSet createDataSetWithMismatchedColumnCount() {
        DataConfiguration config = generateEstimatedConfiguration();
        List<DataRow> rows = new ArrayList<>();
        // Add data with less columns
        rows.add(new DataRow(List.of(new BooleanData(true),
                new DateData(LocalDate.of(2023, 11, 20)),
                new DateTimeData(LocalDateTime.of(2023, 11, 20, 12, 50, 27, 123456000)),
                new DecimalData(4.2f),
                new IntegerData(42)
                // Missing value
        )));
        return new DataSet(rows, config);
    }

    public static DataSet createDataSetWithMismatchedDataTypes() {
        DataConfiguration config = generateEstimatedConfiguration();  // Config avec des types attendus
        List<DataRow> rows = new ArrayList<>();
        // Add data with wrong type
        rows.add(new DataRow(List.of(new IntegerData(1), //Expected type:Boolean
                new DateData(LocalDate.of(2023, 11, 20)),
                new DateTimeData(LocalDateTime.of(2023, 11, 20, 12, 50, 27, 123456000)),
                new DecimalData(4.2f),
                new IntegerData(42),
                new StringData("Hello World!")))); // Dernière donnée devrait être un DecimalData, par exemple
        return new DataSet(rows, config);
    }

    public static DataSet createDataSetForMerge1() {
        // Modified data configuration
        DataConfiguration config = new DataConfiguration();
        config.addColumnConfiguration(new ColumnConfiguration(0, "FellowShipID", DataType.INTEGER, DataScale.INTERVAL, new ArrayList<>()));
        config.addColumnConfiguration(new ColumnConfiguration(1, "FirstName", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()));
        config.addColumnConfiguration(new ColumnConfiguration(2, "Skills", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()));

        // Creating rows with data entries
        List<DataRow> rows = new ArrayList<>();
        rows.add(new DataRow(List.of(new IntegerData(1001), new StringData("Frodo"), new StringData("Hiding"))));
        rows.add(new DataRow(List.of(new IntegerData(1002), new StringData("Samwise"), new StringData("Gardening"))));
        rows.add(new DataRow(List.of(new IntegerData(1003), new StringData("Gandalf"), new StringData("Spells"))));
        rows.add(new DataRow(List.of(new IntegerData(1004), new StringData("Pippin"), new StringData("Fireworks"))));

        // Returning the complete DataSet
        return new DataSet(rows, config);
    }


    public static DataSet createDataSetForMerge2() {
        // Modified data configuration
        DataConfiguration config = new DataConfiguration();
        config.addColumnConfiguration(new ColumnConfiguration(0, "FellowShipID", DataType.INTEGER, DataScale.INTERVAL, new ArrayList<>()));
        config.addColumnConfiguration(new ColumnConfiguration(1, "FirstName", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()));
        config.addColumnConfiguration(new ColumnConfiguration(2, "Age", DataType.INTEGER, DataScale.INTERVAL, new ArrayList<>()));

        // Creating rows with data entries
        List<DataRow> rows = new ArrayList<>();
        rows.add(new DataRow(List.of(new IntegerData(1001), new StringData("Frodo"), new IntegerData(50))));
        rows.add(new DataRow(List.of(new IntegerData(1002), new StringData("Samwise"), new IntegerData(39))));
        rows.add(new DataRow(List.of(new IntegerData(1006), new StringData("Legolas"), new IntegerData(2931))));
        rows.add(new DataRow(List.of(new IntegerData(1007), new StringData("Elrond"), new IntegerData(6520))));
        rows.add(new DataRow(List.of(new IntegerData(1008), new StringData("Barromir"), new IntegerData(51))));


        // Returning the complete DataSet
        return new DataSet(rows, config);
    }

    public static DataSet createDataSetForMerge3() {
        // Modified data configuration
        DataConfiguration config = new DataConfiguration();
        config.addColumnConfiguration(new ColumnConfiguration(0, "FellowShipID", DataType.INTEGER, DataScale.INTERVAL, new ArrayList<>()));
        config.addColumnConfiguration(new ColumnConfiguration(1, "FirstName", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()));
        config.addColumnConfiguration(new ColumnConfiguration(2, "Category", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()));

        // Creating rows with data entries
        List<DataRow> rows = new ArrayList<>();
        rows.add(new DataRow(List.of(new IntegerData(1001), new StringData("Frodo"), new StringData("Hobbit"))));
        rows.add(new DataRow(List.of(new IntegerData(1002), new StringData("Samwise"), new StringData("Hobbit"))));
        rows.add(new DataRow(List.of(new IntegerData(1009), new StringData("Morgoth"), new StringData("Ainur"))));
        rows.add(new DataRow(List.of(new IntegerData(1010), new StringData("Galadriel"), new StringData("Elve"))));



        // Returning the complete DataSet
        return new DataSet(rows, config);
    }








}
