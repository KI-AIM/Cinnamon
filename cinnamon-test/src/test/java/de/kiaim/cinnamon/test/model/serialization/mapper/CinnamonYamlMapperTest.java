package de.kiaim.cinnamon.test.model.serialization.mapper;

import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.model.serialization.mapper.CinnamonJsonMapper;
import de.kiaim.cinnamon.model.serialization.mapper.CinnamonYamlMapper;
import de.kiaim.cinnamon.test.util.DataConfigurationTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CinnamonYamlMapperTest {

	static JsonMapper jsonMapper;
	static YAMLMapper yamlMapper;

	@BeforeAll
	static void beforeAll() {
		jsonMapper = CinnamonJsonMapper.jsonMapper();
		yamlMapper = CinnamonYamlMapper.yamlMapper();
	}

	@Test
	public void serializeDataConfigurationJson() {
		final DataConfiguration dataConfiguration = DataConfigurationTestHelper.generateDataConfiguration();
		final String json = jsonMapper.writeValueAsString(dataConfiguration);
		final String expected = DataConfigurationTestHelper.generateDataConfigurationAsJson();
		assertEquals(expected, json);
	}

	@Test
	public void serializeDataConfigurationYaml() {
		final DataConfiguration dataConfiguration = DataConfigurationTestHelper.generateDataConfiguration();
		final String yaml = yamlMapper.writeValueAsString(dataConfiguration);
		final String expected = DataConfigurationTestHelper.generateDataConfigurationAsYaml();
		assertEquals(expected, yaml);
	}

	@Test
	public void deserializeDataConfigurationJson() {
		final String json = DataConfigurationTestHelper.generateDataConfigurationAsJson();
		final DataConfiguration dataConfiguration = yamlMapper.readValue(json, DataConfiguration.class);
		final DataConfiguration expected = DataConfigurationTestHelper.generateDataConfiguration();
		assertEquals(expected, dataConfiguration);
	}

	@Test
	public void deserializeDataConfigurationYaml() {
		final String yaml = DataConfigurationTestHelper.generateDataConfigurationAsYaml();
		final DataConfiguration dataConfiguration = yamlMapper.readValue(yaml, DataConfiguration.class);
		final DataConfiguration expected = DataConfigurationTestHelper.generateDataConfiguration();
		assertEquals(expected, dataConfiguration);
	}

}
