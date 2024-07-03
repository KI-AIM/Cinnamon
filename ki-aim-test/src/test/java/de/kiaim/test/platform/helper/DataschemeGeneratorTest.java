package de.kiaim.test.platform.helper;

import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.platform.helper.DataschemeGenerator;
import de.kiaim.test.util.DataConfigurationTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataschemeGeneratorTest {

	private static DataschemeGenerator dataschemeGenerator;

	@Test
	void createSchema() {
		final DataConfiguration dataConfiguration = DataConfigurationTestHelper.generateDataConfiguration();
		final String query = dataschemeGenerator.createSchema(dataConfiguration, "data_set_1");

		final String expected = "CREATE TABLE data_set_1(" +
		                        "\"column0_boolean\" boolean," +
		                        "\"column1_date\" date," +
		                        "\"column2_date_time\" timestamp," +
		                        "\"column3_decimal\" numeric," +
		                        "\"column4_integer\" integer," +
		                        "\"column5_string\" character varying" +
		                        ");";

		assertEquals(expected, query);
	}

	@BeforeAll
	static void beforeAll() {
		dataschemeGenerator = new DataschemeGenerator();
	}
}
