package de.kiaim.model;

import de.kiaim.model.data.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class DataSetTestHelper {

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

	public static DataSet generateDataSet(final boolean withErrors) {
		return new DataSet(generateDataRows(withErrors), DataConfigurationTestHelper.generateDataConfiguration());
	}

	public static String generateDataSetAsJson() {
		return generateDataSetAsJson("null", "null");
	}

	public static String generateDataSetAsJson(final String missingValueEncoding, final String formatErrorEncoding) {
		return
				"""
						{"dataConfiguration":""" + generateDataConfigurationAsJson() +
				"""
						,"data":""" + generateDataAsJson(missingValueEncoding, formatErrorEncoding) + "}";
	}

	public static String generateDataAsJson(final String missingValueEncoding, final String formatErrorEncoding) {
		return
				"""
						[[true,"2023-11-20","2023-11-20T12:50:27.123456",4.2,42,"Hello World!"],[false,"2023-11-20","2023-11-20T12:50:27.123456",2.4,24,"Bye World!"],[true,"2023-11-20","""
				+ missingValueEncoding + ",4.2," + formatErrorEncoding +
				"""
						,"Hello World!"]]""";
	}

	public static String generateDataConfigurationAsJson() {
		return """
				{"configurations":[{"index":0,"name":"column0_boolean","type":"BOOLEAN","scale":"NOMINAL","configurations":[]},{"index":1,"name":"column1_date","type":"DATE","scale":"DATE","configurations":[{"name":"DateFormatConfiguration","dateFormatter":"yyyy-MM-dd"},{"name":"RangeConfiguration","minValue":"1970-01-01","maxValue":"2030-01-01"}]},{"index":2,"name":"column2_date_time","type":"DATE_TIME","scale":"DATE","configurations":[{"name":"DateTimeFormatConfiguration","dateTimeFormatter":"yyyy-MM-dd'T'HH:mm:ss.SSSSSS"},{"name":"RangeConfiguration","minValue":"1970-01-01T00:01:00","maxValue":"2030-01-01T23:59:00"}]},{"index":3,"name":"column3_decimal","type":"DECIMAL","scale":"RATIO","configurations":[]},{"index":4,"name":"column4_integer","type":"INTEGER","scale":"INTERVAL","configurations":[{"name":"RangeConfiguration","minValue":0,"maxValue":100}]},{"index":5,"name":"column5_string","type":"STRING","scale":"NOMINAL","configurations":[{"name":"StringPatternConfiguration","pattern":".*"}]}]}""";
	}
}
