package de.kiaim.platform.helper;

import de.kiaim.platform.model.data.DataType;
import de.kiaim.platform.model.data.configuration.ColumnConfiguration;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataschemeGeneratorTest {

	private static DataschemeGenerator dataschemeGenerator;

	@Test
	void createSchema() {
		final DataConfiguration dataConfiguration = new DataConfiguration();
		final List<ColumnConfiguration> columnConfigurations = List.of(
				new ColumnConfiguration(0, "0_boolean", DataType.BOOLEAN, new ArrayList<>()),
				new ColumnConfiguration(1, "1_date", DataType.DATE, new ArrayList<>()),
				new ColumnConfiguration(2, "2_data-time", DataType.DATE_TIME, new ArrayList<>()),
				new ColumnConfiguration(3, "3_decimal", DataType.DECIMAL, new ArrayList<>()),
				new ColumnConfiguration(4, "4_integer", DataType.INTEGER, new ArrayList<>()),
				new ColumnConfiguration(5, "5_string", DataType.STRING, new ArrayList<>()));
		dataConfiguration.setConfigurations(columnConfigurations);
		final String query = dataschemeGenerator.createSchema(dataConfiguration, "data_set_1");

		final String expected = "CREATE TABLE data_set_1(" +
		                        "0_boolean boolean NOT NULL," +
		                        "1_date date NOT NULL," +
		                        "2_date-time timestamp NOT NULL," +
		                        "3_decimal numeric NOT NULL," +
		                        "4_integer integer NOT NULL," +
		                        "5_string character varying NOT NULL" +
		                        ");";

		assertEquals(expected, query);
	}

	@BeforeAll
	static void beforeAll() {
		dataschemeGenerator = new DataschemeGenerator();
	}
}
