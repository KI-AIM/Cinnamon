package de.kiaim.platform;

import de.kiaim.platform.model.*;
import de.kiaim.platform.model.data.*;
import de.kiaim.platform.model.data.configuration.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TestModelHelper {

	public static DataConfiguration generateDataConfiguration() {
		final DataConfiguration dataConfiguration = new DataConfiguration();
		final StringPatternConfiguration stringPatternConfiguration = new StringPatternConfiguration(".*");
		final DateFormatConfiguration dateFormatConfiguration = new DateFormatConfiguration("yyyy-MM-dd");
		final DateTimeFormatConfiguration dateTimeFormatConfiguration = new DateTimeFormatConfiguration("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
		final List<ColumnConfiguration> columnConfigurations = List.of(
				new ColumnConfiguration(0, "column0_boolean", DataType.BOOLEAN, new ArrayList<>()),
				new ColumnConfiguration(1, "column1_date", DataType.DATE, List.of(dateFormatConfiguration)),
				new ColumnConfiguration(2, "column2_date_time", DataType.DATE_TIME, List.of(dateTimeFormatConfiguration)),
				new ColumnConfiguration(3, "column3_decimal", DataType.DECIMAL, new ArrayList<>()),
				new ColumnConfiguration(4, "column4_integer", DataType.INTEGER, new ArrayList<>()),
				new ColumnConfiguration(5, "column5_string", DataType.STRING, List.of(stringPatternConfiguration)));
		dataConfiguration.setConfigurations(columnConfigurations);
		final List<DataType> dataTypes = List.of(DataType.BOOLEAN,
		                                         DataType.DATE,
		                                         DataType.DATE_TIME,
		                                         DataType.DECIMAL,
		                                         DataType.INTEGER,
		                                         DataType.STRING);
		dataConfiguration.setDataTypes(dataTypes);
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

	public static TransformationResult generateTransformationResult() {
		final List<String> rawValues = List.of("true", "2023-11-20", "", "4.2", "42", "Hello World!");
		final DataRowTransformationError dataRowTransformationError = new DataRowTransformationError(2, rawValues);
		final DataTransformationError dataTransformationError = new DataTransformationError(2,
		                                                                                    TransformationErrorType.MISSING_VALUE);
		dataRowTransformationError.addError(dataTransformationError);
		final List<DataRowTransformationError> dataRowTransformationErrors = List.of(dataRowTransformationError);

		return new TransformationResult(generateDataSet(), dataRowTransformationErrors);
	}
}
