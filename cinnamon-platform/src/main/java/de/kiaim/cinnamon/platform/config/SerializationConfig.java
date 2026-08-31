package de.kiaim.cinnamon.platform.config;

import de.kiaim.cinnamon.model.serialization.mapper.CinnamonJsonMapper;
import de.kiaim.cinnamon.model.serialization.mapper.CinnamonYamlMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Configuration
public class SerializationConfig {

	public JsonMapper jsonMapper() {
		return CinnamonJsonMapper.jsonMapper();
	}

	@Bean
	public YAMLMapper yamlMapper() {
		return CinnamonYamlMapper.yamlMapper();
	}
}
