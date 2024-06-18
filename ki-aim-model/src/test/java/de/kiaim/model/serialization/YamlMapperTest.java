package de.kiaim.model.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.DataConfigurationTestHelper;
import de.kiaim.model.configuration.DataConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class YamlMapperTest {

	static ObjectMapper yamlMapper;

	@BeforeAll
	static void beforeAll() {
		yamlMapper = YamlMapper.yamlMapper();
	}

	@Test
	public void serializeDataConfiguration() throws JsonProcessingException {
		final DataConfiguration dataConfiguration = DataConfigurationTestHelper.generateDataConfiguration();
		final String yaml = yamlMapper.writeValueAsString(dataConfiguration);
		final String expected = DataConfigurationTestHelper.generateDataConfigurationAsYaml();
		assertEquals(expected, yaml);
	}

	@Test
	public void deserializeDataConfiguration() throws JsonProcessingException {
		final String yaml = DataConfigurationTestHelper.generateDataConfigurationAsYaml();
		final DataConfiguration dataConfiguration = yamlMapper.readValue(yaml, DataConfiguration.class);
		final DataConfiguration expected = DataConfigurationTestHelper.generateDataConfiguration();
		assertEquals(expected, dataConfiguration);
	}

}
