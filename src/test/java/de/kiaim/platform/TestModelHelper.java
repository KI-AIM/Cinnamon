package de.kiaim.platform;

import de.kiaim.platform.model.DataSet;
import de.kiaim.platform.model.data.*;
import de.kiaim.platform.model.data.configuration.ColumnConfiguration;
import de.kiaim.platform.model.data.configuration.DataConfiguration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TestModelHelper {

	public static DataConfiguration generateDataConfiguration() {
		final DataConfiguration dataConfiguration = new DataConfiguration();
		final List<ColumnConfiguration> columnConfigurations = List.of(
				new ColumnConfiguration(0, "column0_boolean", DataType.BOOLEAN, new ArrayList<>()),
				new ColumnConfiguration(1, "column1_date", DataType.DATE, new ArrayList<>()),
				new ColumnConfiguration(2, "column2_date_time", DataType.DATE_TIME, new ArrayList<>()),
				new ColumnConfiguration(3, "column3_decimal", DataType.DECIMAL, new ArrayList<>()),
				new ColumnConfiguration(4, "column4_integer", DataType.INTEGER, new ArrayList<>()),
				new ColumnConfiguration(5, "column5_string", DataType.STRING, new ArrayList<>()));
		dataConfiguration.setConfigurations(columnConfigurations);
		return dataConfiguration;
	}

	public static List<DataRow> generateDataRows() {
		final List<Data> data1 = List.of(new BooleanData(true),
		                                 new DateData(LocalDate.of(2023, 11, 20)),
		                                 new DateTimeData(LocalDateTime.of(2023, 11, 20, 12, 50, 27, 123456789)),
		                                 new DecimalData(4.2f),
		                                 new IntegerData(42),
		                                 new StringData("Hello World!"));
		final List<Data> data2 = List.of(new BooleanData(false),
		                                 new DateData(LocalDate.of(2023, 11, 20)),
		                                 new DateTimeData(LocalDateTime.of(2023, 11, 20, 12, 50, 27, 123456789)),
		                                 new DecimalData(2.4f),
		                                 new IntegerData(24),
		                                 new StringData("Bye World!"));
		final DataRow dataRow1 = new DataRow(data1);
		final DataRow dataRow2 = new DataRow(data2);
		return List.of(dataRow1, dataRow2);
	}

	public static DataSet generateDataSet() {
		return new DataSet(generateDataRows(), generateDataConfiguration());
	}
}
