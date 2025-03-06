package de.kiaim.cinnamon.test.platform.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import de.kiaim.cinnamon.test.util.DataConfigurationTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataConfigurationTest extends ContextRequiredTest {

	@Autowired
	ObjectMapper objectMapper;

	@Test
	public void serializationTest() throws JsonProcessingException {
		final DataConfiguration dataConfiguration = DataConfigurationTestHelper.generateDataConfiguration();
		final String json = objectMapper.writeValueAsString(dataConfiguration);
		final String expected = DataConfigurationTestHelper.generateDataConfigurationAsYaml();
		assertEquals(expected, json);
	}

	@Test
	public void deserializationTest() throws JsonProcessingException {
		final String json = DataConfigurationTestHelper.generateDataConfigurationAsYaml();
		final DataConfiguration dataConfiguration = objectMapper.readValue(json, DataConfiguration.class);
		final DataConfiguration expected = DataConfigurationTestHelper.generateDataConfiguration();
		assertEquals(expected, dataConfiguration);
	}
}
