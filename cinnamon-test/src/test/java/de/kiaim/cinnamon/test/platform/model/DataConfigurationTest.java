package de.kiaim.cinnamon.test.platform.model;

import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.test.platform.ContextRequiredTest;
import de.kiaim.cinnamon.test.util.DataConfigurationTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataConfigurationTest extends ContextRequiredTest {

	@Autowired
	YAMLMapper yamlMapper;

	@Test
	public void serializationTest() {
		final DataConfiguration dataConfiguration = DataConfigurationTestHelper.generateDataConfiguration();
		final String json = yamlMapper.writeValueAsString(dataConfiguration);
		final String expected = DataConfigurationTestHelper.generateDataConfigurationAsYaml();
		assertEquals(expected, json);
	}

	@Test
	public void deserializationTest() {
		final String json = DataConfigurationTestHelper.generateDataConfigurationAsYaml();
		final DataConfiguration dataConfiguration = yamlMapper.readValue(json, DataConfiguration.class);
		final DataConfiguration expected = DataConfigurationTestHelper.generateDataConfiguration();
		assertEquals(expected, dataConfiguration);
	}
}
