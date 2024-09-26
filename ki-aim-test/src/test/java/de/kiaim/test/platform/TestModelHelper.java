package de.kiaim.test.platform;

import de.kiaim.model.configuration.data.*;
import de.kiaim.model.data.*;
import de.kiaim.model.enumeration.DataScale;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.model.enumeration.TransformationErrorType;
import de.kiaim.platform.model.DataRowTransformationError;
import de.kiaim.platform.model.DataTransformationError;
import de.kiaim.platform.model.TransformationResult;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class TestModelHelper {

	public static DataConfiguration generateDataConfiguration(final String stringPattern) {
		final DataConfiguration dataConfiguration = generateEstimatedConfiguration();

		final DateFormatConfiguration dateFormatConfiguration = new DateFormatConfiguration("yyyy-MM-dd");
		dataConfiguration.getConfigurations().get(1).addConfiguration(dateFormatConfiguration);
		final RangeConfiguration dateRangeConfiguration = new RangeConfiguration(new DateData(LocalDate.of(1970, 1, 1)),
		                                                                         new DateData(
				                                                                         LocalDate.of(2030, 1, 1)));
		dataConfiguration.getConfigurations().get(1).addConfiguration(dateRangeConfiguration);

		final DateTimeFormatConfiguration dateTimeFormatConfiguration = new DateTimeFormatConfiguration(
				"yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
		dataConfiguration.getConfigurations().get(2).addConfiguration(dateTimeFormatConfiguration);
		final RangeConfiguration dateTimeRangeConfiguration = new RangeConfiguration(
				new DateTimeData(LocalDateTime.of(1970, 1, 1, 0, 1)),
				new DateTimeData(LocalDateTime.of(2030, 1, 1, 23, 59)));
		dataConfiguration.getConfigurations().get(2).addConfiguration(dateTimeRangeConfiguration);

		final RangeConfiguration integerRangeConfiguration = new RangeConfiguration(new IntegerData(0),
		                                                                            new IntegerData(100));
		dataConfiguration.getConfigurations().get(4).addConfiguration(integerRangeConfiguration);

		final StringPatternConfiguration stringPatternConfiguration = new StringPatternConfiguration(stringPattern);
		dataConfiguration.getConfigurations().get(5).addConfiguration(stringPatternConfiguration);

		return dataConfiguration;
	}

	public static DataConfiguration generateDataConfiguration() {
		return generateDataConfiguration(".*");
	}

	public static DataConfiguration generateEstimatedConfiguration() {
		final DataConfiguration configuration = new DataConfiguration();
		final List<ColumnConfiguration> columnConfigurations = List.of(
				new ColumnConfiguration(0, "column0_boolean", DataType.BOOLEAN, DataScale.NOMINAL, new ArrayList<>()),
				new ColumnConfiguration(1, "column1_date", DataType.DATE, DataScale.DATE, new ArrayList<>()),
				new ColumnConfiguration(2, "column2_date_time", DataType.DATE_TIME, DataScale.DATE, new ArrayList<>()),
				new ColumnConfiguration(3, "column3_decimal", DataType.DECIMAL, DataScale.RATIO, new ArrayList<>()),
				new ColumnConfiguration(4, "column4_integer", DataType.INTEGER, DataScale.INTERVAL, new ArrayList<>()),
				new ColumnConfiguration(5, "column5_string", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()));
		configuration.setConfigurations(columnConfigurations);

		return configuration;
	}

	public static List<DataRow> generateDataRows(final boolean withErrors) {
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


		final List<DataRow> dataRows;

		if (withErrors) {
			final List<Data> data3 = List.of(new BooleanData(true),
			                                 new DateData(LocalDate.of(2023, 11, 20)),
			                                 new DateTimeData(null),
			                                 new DecimalData(4.2f),
			                                 new IntegerData(null),
			                                 new StringData("Hello World!"));
			final DataRow dataRow3 = new DataRow(data3);

			dataRows = List.of(dataRow1, dataRow2, dataRow3);
		} else {
			dataRows = List.of(dataRow1, dataRow2);
		}

		return dataRows;
	}

	public static DataSet generateDataSet() {
		return generateDataSet(false);
	}

	public static DataSet generateDataSet(final boolean withErrors) {
		return new DataSet(generateDataRows(withErrors), generateDataConfiguration());
	}

	public static TransformationResult generateTransformationResult(final boolean withErrors) {

		if (withErrors) {
			final DataRowTransformationError dataRowTransformationError = new DataRowTransformationError(2);

			final DataTransformationError missingValueError = new DataTransformationError(2,
			                                                                              TransformationErrorType.MISSING_VALUE,
			                                                                              "");
			dataRowTransformationError.addError(missingValueError);

			final DataTransformationError formatError = new DataTransformationError(4,
			                                                                        TransformationErrorType.FORMAT_ERROR,
			                                                                        "forty two");
			dataRowTransformationError.addError(formatError);

			final List<DataRowTransformationError> dataRowTransformationErrors = List.of(dataRowTransformationError);
			return new TransformationResult(generateDataSet(true), dataRowTransformationErrors);
		} else {
			return new TransformationResult(generateDataSet(), new ArrayList<>());
		}
	}

	public static String generateDataSetColumnsAsYaml() {
		return """
				dataConfiguration:
				  configurations:
				  - index: 0
				    name: "column4_integer"
				    type: "INTEGER"
				    scale: "INTERVAL"
				    configurations:
				    - name: "RangeConfiguration"
				      minValue: 0
				      maxValue: 100
				  - index: 1
				    name: "column0_boolean"
				    type: "BOOLEAN"
				    scale: "NOMINAL"
				    configurations: []
				data:
				""" + generateDataColumnsAsYaml();
	}

	public static String generateDataColumnsAsYaml() {
		return """
				- - 42
				  - true
				- - 24
				  - false
				- - null
				  - true
				""";
	}

	public static String generateDataSetAsYaml() {
		return generateDataSetAsYaml("null", "null");
	}

	public static String generateDataSetAsYaml(final String missingValueEncoding, final String formatErrorEncoding) {
		return
				"""
						dataConfiguration:
						""" + indentYaml(generateDataConfigurationAsYaml()) +
				"""
						data:
						""" + generateDataAsYaml(missingValueEncoding, formatErrorEncoding);
	}

	public static String generateDataAsYaml() {
		return generateDataAsYaml("null", "null");
	}

	public static String generateDataAsYaml(final String missingValueEncoding, final String formatErrorEncoding) {
		return
				"""
						- - true
						  - "2023-11-20"
						  - "2023-11-20T12:50:27.123456"
						  - 4.2
						  - 42
						  - "Hello World!"
						- - false
						  - "2023-11-20"
						  - "2023-11-20T12:50:27.123456"
						  - 2.4
						  - 24
						  - "Bye World!"
						- - true
						  - "2023-11-20"
						  -\s""" + missingValueEncoding +
				"""
					
						\s\s- 4.2
						  -\s""" + formatErrorEncoding +
				"""
						
						  - "Hello World!"
						""";
	}

	public static String generateTransformationResultAsYaml() {
		return
				"""
						dataSet:
						""" + indentYaml(generateDataSetAsYaml()) +
				"""
						transformationErrors:
						- index: 2
						  rawValues:
						  - "true"
						  - "2023-11-20"
						  - ""
						  - "4.2"
						  - "forty two"
						  - "Hello World!"
						  dataTransformationErrors:
						  - index: 2
						    errorType: "MISSING_VALUE"
						  - index: 4
						    errorType: "FORMAT_ERROR"
						""";
	}

	public static String generateDataConfigurationAsJson() {
		return """
				{"configurations":[{"index":0,"name":"column0_boolean","type":"BOOLEAN","scale":"NOMINAL","configurations":[]},{"index":1,"name":"column1_date","type":"DATE","scale":"DATE","configurations":[{"name":"DateFormatConfiguration","dateFormatter":"yyyy-MM-dd"},{"name":"RangeConfiguration","minValue":"1970-01-01","maxValue":"2030-01-01"}]},{"index":2,"name":"column2_date_time","type":"DATE_TIME","scale":"DATE","configurations":[{"name":"DateTimeFormatConfiguration","dateTimeFormatter":"yyyy-MM-dd'T'HH:mm:ss.SSSSSS"},{"name":"RangeConfiguration","minValue":"1970-01-01T00:01:00","maxValue":"2030-01-01T23:59:00"}]},{"index":3,"name":"column3_decimal","type":"DECIMAL","scale":"RATIO","configurations":[]},{"index":4,"name":"column4_integer","type":"INTEGER","scale":"INTERVAL","configurations":[{"name":"RangeConfiguration","minValue":0,"maxValue":100}]},{"index":5,"name":"column5_string","type":"STRING","scale":"NOMINAL","configurations":[{"name":"StringPatternConfiguration","pattern":".*"}]}]}""";
	}

	public static String generateDataConfigurationAsYaml() {
		return """
				configurations:
				- index: 0
				  name: "column0_boolean"
				  type: "BOOLEAN"
				  scale: "NOMINAL"
				  configurations: []
				- index: 1
				  name: "column1_date"
				  type: "DATE"
				  scale: "DATE"
				  configurations:
				  - name: "DateFormatConfiguration"
				    dateFormatter: "yyyy-MM-dd"
				  - name: "RangeConfiguration"
				    minValue: "1970-01-01"
				    maxValue: "2030-01-01"
				- index: 2
				  name: "column2_date_time"
				  type: "DATE_TIME"
				  scale: "DATE"
				  configurations:
				  - name: "DateTimeFormatConfiguration"
				    dateTimeFormatter: "yyyy-MM-dd'T'HH:mm:ss.SSSSSS"
				  - name: "RangeConfiguration"
				    minValue: "1970-01-01T00:01:00"
				    maxValue: "2030-01-01T23:59:00"
				- index: 3
				  name: "column3_decimal"
				  type: "DECIMAL"
				  scale: "RATIO"
				  configurations: []
				- index: 4
				  name: "column4_integer"
				  type: "INTEGER"
				  scale: "INTERVAL"
				  configurations:
				  - name: "RangeConfiguration"
				    minValue: 0
				    maxValue: 100
				- index: 5
				  name: "column5_string"
				  type: "STRING"
				  scale: "NOMINAL"
				  configurations:
				  - name: "StringPatternConfiguration"
				    pattern: ".*"
				""";
	}

	public static MockMultipartFile loadCsvFile() throws IOException {
		ClassLoader classLoader = TestModelHelper.class.getClassLoader();
		return new MockMultipartFile("file", "file.csv", null,
		                             classLoader.getResourceAsStream("test.csv"));
	}

	public static MockMultipartFile loadCsvFileWithErrors() throws IOException {
		ClassLoader classLoader = TestModelHelper.class.getClassLoader();
		return new MockMultipartFile("file", "file.csv", null,
		                             classLoader.getResourceAsStream("testWithErrors.csv"));
	}

	private static String indentYaml(final String value) {
		final String indent = "  ";
		return indent + value.replaceAll("(?:\r\n?|\n)(?!\\z)", "$0" + Matcher.quoteReplacement(indent));
	}
}
