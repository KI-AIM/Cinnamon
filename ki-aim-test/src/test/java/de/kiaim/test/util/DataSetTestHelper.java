package de.kiaim.test.util;

import de.kiaim.model.data.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static de.kiaim.test.util.YamlUtil.indentYaml;

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

	public static DataSet generateDataSet() {
		return generateDataSet(false);
	}

	public static String generateDataSetAsJson() {
		return generateDataSetAsJson(true, "null", "null");
	}

	public static String generateDataSetAsJson(final boolean withErrors) {
		return generateDataSetAsJson(withErrors, "null", "null");
	}

	public static String generateDataSetAsJson(final boolean withErrors, final String missingValueEncoding,
	                                           final String formatErrorEncoding) {
		return
				"""
						{"dataConfiguration":""" + DataConfigurationTestHelper.generateDataConfigurationAsJson() +
				"""
						,"data":""" + generateDataAsJson(withErrors, missingValueEncoding, formatErrorEncoding) + "}";
	}

	public static String generateDataAsJson(final boolean withErrors, final String missingValueEncoding,
	                                        final String formatErrorEncoding) {
		String data = """
				[[true,"2023-11-20","2023-11-20T12:50:27.123456",4.2,42,"Hello World!"],[false,"2023-11-20","2023-11-20T12:50:27.123456",2.4,24,"Bye World!"]""";

		if (withErrors) {
			data += """
					        ,[true,"2023-11-20",""" + missingValueEncoding + ",4.2," + formatErrorEncoding +
			        """
					        ,"Hello World!"]""";
		}

		data += "]";

		return data;
	}

	public static String generateDataSetAsYaml() {
		return generateDataSetAsYaml("null", "null");
	}

	public static String generateDataSetAsYaml(final String missingValueEncoding, final String formatErrorEncoding) {
		return
				"""
						dataConfiguration:
						""" + indentYaml(DataConfigurationTestHelper.generateDataConfigurationAsYaml()) +
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
}
