package de.kiaim.cinnamon.test.model.serialization;

import de.kiaim.cinnamon.model.configuration.ConfigurationPart;
import de.kiaim.cinnamon.model.configuration.ExternalConfigurationWrapper;
import de.kiaim.cinnamon.model.serialization.mapper.CinnamonJsonMapper;
import de.kiaim.cinnamon.model.serialization.mapper.CinnamonYamlMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalConfigurationWrapperTest {

	private static JsonMapper jsonMapper;
	private static YAMLMapper yamlMapper;

	@BeforeAll
	static void beforeAll() {
		jsonMapper = CinnamonJsonMapper.jsonMapper();
		yamlMapper = CinnamonYamlMapper.yamlMapper();
	}

	@Test
	void deserializesJson() {
		final ExternalConfigurationWrapper wrapper = jsonMapper.readValue(
				"{\"externalModule\":{\"algorithm\":{\"name\":\"algorithmA\"},\"parameter\":\"value\"}}",
				ExternalConfigurationWrapper.class);

		assertEquals("externalModule", wrapper.getKey());
		assertEquals("value", wrapper.asMap().get("externalModule").getConfiguration().get("parameter").asString());
	}

	@Test
	void deserializesYaml() {
		final ExternalConfigurationWrapper wrapper = yamlMapper.readValue(
				"externalModule:\n  parameter: value\n",
				ExternalConfigurationWrapper.class);

		assertEquals("externalModule", wrapper.getKey());
		assertEquals("value", wrapper.asMap().get("externalModule").getConfiguration().get("parameter").asString());
	}

	@Test
	void serializesWithDynamicPropertyName() {
		final ExternalConfigurationWrapper wrapper = new ExternalConfigurationWrapper("externalModule", new ConfigurationPart());

		assertEquals("{\"externalModule\":{}}", jsonMapper.writeValueAsString(wrapper));
	}

	@Test
	void rejectsInvalidTopLevelPropertyCount() {
		assertThrows(JacksonException.class,
		             () -> jsonMapper.readValue("{}", ExternalConfigurationWrapper.class));
		assertThrows(JacksonException.class,
		             () -> jsonMapper.readValue("{\"first\":{},\"second\":{}}", ExternalConfigurationWrapper.class));
	}
}
