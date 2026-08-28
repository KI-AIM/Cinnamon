package de.kiaim.cinnamon.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.serialization.mapper.CinnamonJsonMapper;
import de.kiaim.cinnamon.model.serialization.mapper.CinnamonYamlMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SerializationConfig {

//	@Bean
	public ObjectMapper jsonMapper() {
		return CinnamonJsonMapper.jsonMapper();
	}

	@Bean
	public ObjectMapper yamlMapper() {
		return CinnamonYamlMapper.yamlMapper();
	}
}
