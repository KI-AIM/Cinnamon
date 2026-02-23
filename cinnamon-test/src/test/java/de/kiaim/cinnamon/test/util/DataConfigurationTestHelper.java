package de.kiaim.cinnamon.test.util;

import de.kiaim.cinnamon.model.configuration.data.*;
import de.kiaim.cinnamon.model.data.DateData;
import de.kiaim.cinnamon.model.data.DateTimeData;
import de.kiaim.cinnamon.model.data.IntegerData;
import de.kiaim.cinnamon.model.enumeration.DataScale;
import de.kiaim.cinnamon.model.enumeration.DataType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DataConfigurationTestHelper {

	public static DataConfiguration generateDataConfiguration(final String stringPattern) {
		return generateDataConfiguration(stringPattern, false);
	}

	public static DataConfiguration generateDataConfiguration(final String stringPattern, final boolean alternative) {
		final DataConfiguration dataConfiguration = generateEstimatedConfiguration(alternative);

		final RangeConfiguration dateRangeConfiguration = new RangeConfiguration(new DateData(LocalDate.of(1970, 1, 1)), new DateData(LocalDate.of(2030, 1, 1)));
		dataConfiguration.getConfigurations().get(1).addConfiguration(dateRangeConfiguration);

		final RangeConfiguration dateTimeRangeConfiguration = new RangeConfiguration(new DateTimeData(LocalDateTime.of(1970, 1, 1, 0, 1)), new DateTimeData(LocalDateTime.of(2030, 1, 1, 23, 59)));
		dataConfiguration.getConfigurations().get(2).addConfiguration(dateTimeRangeConfiguration);

		final RangeConfiguration integerRangeConfiguration = new RangeConfiguration(new IntegerData(0), new IntegerData(100));
		dataConfiguration.getConfigurations().get(4).addConfiguration(integerRangeConfiguration);

		final StringPatternConfiguration stringPatternConfiguration = new StringPatternConfiguration(stringPattern);
		dataConfiguration.getConfigurations().get(5).addConfiguration(stringPatternConfiguration);

		return dataConfiguration;
	}

	public static DataConfiguration generateDataConfiguration() {
		return generateDataConfiguration(false);
	}

	public static DataConfiguration generateDataConfiguration(final boolean alternative) {
		return generateDataConfiguration(".*", alternative);
	}

	public static DataConfiguration generateEstimatedConfiguration() {
		return generateEstimatedConfiguration(false);
	}

	public static DataConfiguration generateEstimatedConfiguration(final boolean alternative) {
		final String dateFormat = alternative ? "dd.MM.yyyy" : "yyyy-MM-dd";
		final String dateTimeFormat = alternative ? "dd.MM.yyyy HH:mm:ss" : "yyyy-MM-dd'T'HH:mm:ss.SSSSSS";

		final DataConfiguration configuration = new DataConfiguration();
		final List<ColumnConfiguration> columnConfigurations = List.of(
				new ColumnConfiguration(0, "column0_boolean", DataType.BOOLEAN, DataScale.NOMINAL , new ArrayList<>()),
				new ColumnConfiguration(1, "column1_date", DataType.DATE, DataScale.DATE, new ArrayList<>(List.of(new DateFormatConfiguration(dateFormat)))),
				new ColumnConfiguration(2, "column2_date_time", DataType.DATE_TIME, DataScale.DATE, new ArrayList<>(List.of(new DateTimeFormatConfiguration(dateTimeFormat)))),
				new ColumnConfiguration(3, "column3_decimal", DataType.DECIMAL, DataScale.RATIO, new ArrayList<>()),
				new ColumnConfiguration(4, "column4_integer", DataType.INTEGER, DataScale.INTERVAL, new ArrayList<>()),
				new ColumnConfiguration(5, "column5_string", DataType.STRING, DataScale.NOMINAL, new ArrayList<>()));
		configuration.setConfigurations(columnConfigurations);

		return configuration;
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
}
