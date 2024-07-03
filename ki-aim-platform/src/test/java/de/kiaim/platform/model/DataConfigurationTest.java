package de.kiaim.platform.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.platform.ContextRequiredTest;
import de.kiaim.platform.TestModelHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataConfigurationTest extends ContextRequiredTest {

	@Autowired
	ObjectMapper objectMapper;

	@Test
	public void serializationTest() throws JsonProcessingException {
		final DataConfiguration dataConfiguration = TestModelHelper.generateDataConfiguration();
		final String json = objectMapper.writeValueAsString(dataConfiguration);
		final String expected = TestModelHelper.generateDataConfigurationAsYaml();
		assertEquals(expected, json);
	}

	@Test
	public void deserializationTest() throws JsonProcessingException {
		final String json = TestModelHelper.generateDataConfigurationAsYaml();
		final DataConfiguration dataConfiguration = objectMapper.readValue(json, DataConfiguration.class);
		final DataConfiguration expected = TestModelHelper.generateDataConfiguration();
		assertEquals(expected, dataConfiguration);
	}
}
