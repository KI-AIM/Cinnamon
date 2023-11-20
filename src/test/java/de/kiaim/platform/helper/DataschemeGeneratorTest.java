package de.kiaim.platform.helper;

import de.kiaim.platform.model.DataConfiguration;
import de.kiaim.platform.model.data.DataType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataschemeGeneratorTest {

	private static DataschemeGenerator dataschemeGenerator;

	@Test
	void createSchema() {
		final List<DataType> dataTypes = List.of(DataType.BOOLEAN,
		                                         DataType.DATE,
		                                         DataType.DATE_TIME,
		                                         DataType.DECIMAL,
		                                         DataType.INTEGER,
		                                         DataType.STRING);
		final DataConfiguration dataConfiguration = new DataConfiguration();
		dataConfiguration.setDataTypes(dataTypes);
		final String query = dataschemeGenerator.createSchema(dataConfiguration, "data_set_1");

		final String expected = "CREATE TABLE data_set_1(" +
		                        " boolean NOT NULL," +
		                        " date NOT NULL," +
		                        " timestamp NOT NULL," +
		                        " numeric NOT NULL," +
		                        " integer NOT NULL," +
		                        " character varying NOT NULL" +
		                        ");";

		assertEquals(expected, query);
	}

	@BeforeAll
	static void beforeAll() {
		dataschemeGenerator = new DataschemeGenerator();
	}
}
