package de.kiaim.cinnamon.test.model.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.configuration.ConfigurationPart;
import de.kiaim.cinnamon.model.configuration.ExternalConfigurationWrapper;
import de.kiaim.cinnamon.model.serialization.mapper.CinnamonJsonMapper;
import de.kiaim.cinnamon.model.serialization.mapper.CinnamonYamlMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalConfigurationWrapperTest {

	private static ObjectMapper jsonMapper;
	private static ObjectMapper yamlMapper;

	@BeforeAll
	static void beforeAll() {
		jsonMapper = CinnamonJsonMapper.jsonMapper();
		yamlMapper = CinnamonYamlMapper.yamlMapper();
	}

	@Test
	void deserializesJson() throws JsonProcessingException {
		final ExternalConfigurationWrapper wrapper = jsonMapper.readValue(
				"{\"externalModule\":{\"algorithm\":{\"name\":\"algorithmA\"},\"parameter\":\"value\"}}",
				ExternalConfigurationWrapper.class);

		assertEquals("externalModule", wrapper.getKey());
		assertEquals("value", wrapper.asMap().get("externalModule").getConfiguration().get("parameter").asText());
	}

	@Test
	void deserializesYaml() throws JsonProcessingException {
		final ExternalConfigurationWrapper wrapper = yamlMapper.readValue(
				"externalModule:\n  parameter: value\n",
				ExternalConfigurationWrapper.class);

		assertEquals("externalModule", wrapper.getKey());
		assertEquals("value", wrapper.asMap().get("externalModule").getConfiguration().get("parameter").asText());
	}

	@Test
	void serializesWithDynamicPropertyName() throws JsonProcessingException {
		final ExternalConfigurationWrapper wrapper = new ExternalConfigurationWrapper("externalModule", new ConfigurationPart());

		assertEquals("{\"externalModule\":{}}", jsonMapper.writeValueAsString(wrapper));
	}

	@Test
	void rejectsInvalidTopLevelPropertyCount() {
		assertThrows(JsonProcessingException.class,
		             () -> jsonMapper.readValue("{}", ExternalConfigurationWrapper.class));
		assertThrows(JsonProcessingException.class,
		             () -> jsonMapper.readValue("{\"first\":{},\"second\":{}}", ExternalConfigurationWrapper.class));
	}
}
