package de.kiaim.cinnamon.test.platform.helper;

import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.platform.exception.BadDataConfigurationException;
import de.kiaim.cinnamon.platform.helper.DataschemeGenerator;
import de.kiaim.cinnamon.test.util.DataConfigurationTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataschemeGeneratorTest {

	private static DataschemeGenerator dataschemeGenerator;

	@BeforeAll
	static void beforeAll() {
		dataschemeGenerator = new DataschemeGenerator();
	}

	@Test
	void createSchema() {
		final DataConfiguration dataConfiguration = DataConfigurationTestHelper.generateDataConfiguration();
		final String query = assertDoesNotThrow(
				() -> dataschemeGenerator.createSchema(dataConfiguration, "data_set_1")
		);

		final String expected = "CREATE TABLE data_set_1(" +
		                        "\"column0_boolean\" boolean," +
		                        "\"column1_date\" date," +
		                        "\"column2_date_time\" timestamp," +
		                        "\"column3_decimal\" numeric," +
		                        "\"column4_integer\" integer," +
		                        "\"column5_string\" character varying," +
		                        "\"is_hold_out\" boolean," +
		                        "\"row_index\" integer" +
		                        ");";

		assertEquals(expected, query);
	}

	@Test
	void createSchemaWithUndefinedDataType() {
		final DataConfiguration dataConfiguration = DataConfigurationTestHelper.generateDataConfiguration();
		dataConfiguration.getConfigurations().get(0).setType(DataType.UNDEFINED);
		final BadDataConfigurationException exception = assertThrows(BadDataConfigurationException.class,
				() -> dataschemeGenerator.createSchema(dataConfiguration, "data_set_1")
		);

		assertEquals("PLATFORM_1_9_2", exception.getErrorCode(), "Unexpected error code!");
	}
}
