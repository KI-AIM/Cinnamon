package de.kiaim.platform.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.platform.TestModelHelper;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class DataConfigurationTest {

	@Autowired
	ObjectMapper objectMapper;

	@Test
	public void serializationTest() throws JsonProcessingException {
		final DataConfiguration dataConfiguration = TestModelHelper.generateDataConfiguration();
		final String json = objectMapper.writeValueAsString(dataConfiguration);
		final String expected = generateDataConfigurationAsJson();
		assertEquals(expected, json);
	}

	@Test
	public void deserializationTest() throws JsonProcessingException {
		final String json = generateDataConfigurationAsJson();
		final DataConfiguration dataConfiguration = objectMapper.readValue(json, DataConfiguration.class);
		final DataConfiguration expected = TestModelHelper.generateDataConfiguration();
		assertEquals(expected, dataConfiguration);
	}

	private static String generateDataConfigurationAsJson() {
		return """
				{"dataTypes":["BOOLEAN","DATE","DATE_TIME","DECIMAL","INTEGER","STRING"],"configurations":[{"index":0,"name":"column0_boolean","type":"BOOLEAN","configurations":[]},{"index":1,"name":"column1_date","type":"DATE","configurations":[{"name":"DateFormatConfiguration","dateFormatter":"yyyy-MM-dd"}]},{"index":2,"name":"column2_date_time","type":"DATE_TIME","configurations":[{"name":"DateTimeFormatConfiguration","dateTimeFormatter":"yyyy-MM-dd'T'HH:mm:ss.SSSXXX"}]},{"index":3,"name":"column3_decimal","type":"DECIMAL","configurations":[]},{"index":4,"name":"column4_integer","type":"INTEGER","configurations":[]},{"index":5,"name":"column5_string","type":"STRING","configurations":[{"name":"StringPatternConfiguration","pattern":".*"}]}]}""";
	}
}
