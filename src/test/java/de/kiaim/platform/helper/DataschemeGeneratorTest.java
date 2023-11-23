package de.kiaim.platform.helper;

import de.kiaim.platform.TestModelHelper;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataschemeGeneratorTest {

	private static DataschemeGenerator dataschemeGenerator;

	@Test
	void createSchema() {
		final DataConfiguration dataConfiguration = TestModelHelper.generateDataConfiguration();
		final String query = dataschemeGenerator.createSchema(dataConfiguration, "data_set_1");

		final String expected = "CREATE TABLE data_set_1(" +
		                        "column0_boolean boolean NOT NULL," +
		                        "column1_date date NOT NULL," +
		                        "column2_date_time timestamp NOT NULL," +
		                        "column3_decimal numeric NOT NULL," +
		                        "column4_integer integer NOT NULL," +
		                        "column5_string character varying NOT NULL" +
		                        ");";

		assertEquals(expected, query);
	}

	@BeforeAll
	static void beforeAll() {
		dataschemeGenerator = new DataschemeGenerator();
	}
}
