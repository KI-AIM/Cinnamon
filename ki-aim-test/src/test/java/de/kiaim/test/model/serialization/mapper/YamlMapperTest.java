package de.kiaim.test.model.serialization.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.serialization.mapper.JsonMapper;
import de.kiaim.model.serialization.mapper.YamlMapper;
import de.kiaim.test.util.DataConfigurationTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class YamlMapperTest {

	static ObjectMapper jsonMapper;
	static ObjectMapper yamlMapper;

	@BeforeAll
	static void beforeAll() {
		jsonMapper = JsonMapper.jsonMapper();
		yamlMapper = YamlMapper.yamlMapper();
	}

	@Test
	public void serializeDataConfigurationJson() throws JsonProcessingException {
		final DataConfiguration dataConfiguration = DataConfigurationTestHelper.generateDataConfiguration();
		final String json = jsonMapper.writeValueAsString(dataConfiguration);
		final String expected = DataConfigurationTestHelper.generateDataConfigurationAsJson();
		assertEquals(expected, json);
	}

	@Test
	public void serializeDataConfigurationYaml() throws JsonProcessingException {
		final DataConfiguration dataConfiguration = DataConfigurationTestHelper.generateDataConfiguration();
		final String yaml = yamlMapper.writeValueAsString(dataConfiguration);
		final String expected = DataConfigurationTestHelper.generateDataConfigurationAsYaml();
		assertEquals(expected, yaml);
	}

	@Test
	public void deserializeDataConfigurationJson() throws JsonProcessingException {
		final String json = DataConfigurationTestHelper.generateDataConfigurationAsJson();
		final DataConfiguration dataConfiguration = yamlMapper.readValue(json, DataConfiguration.class);
		final DataConfiguration expected = DataConfigurationTestHelper.generateDataConfiguration();
		assertEquals(expected, dataConfiguration);
	}

	@Test
	public void deserializeDataConfigurationYaml() throws JsonProcessingException {
		final String yaml = DataConfigurationTestHelper.generateDataConfigurationAsYaml();
		final DataConfiguration dataConfiguration = yamlMapper.readValue(yaml, DataConfiguration.class);
		final DataConfiguration expected = DataConfigurationTestHelper.generateDataConfiguration();
		assertEquals(expected, dataConfiguration);
	}

}
