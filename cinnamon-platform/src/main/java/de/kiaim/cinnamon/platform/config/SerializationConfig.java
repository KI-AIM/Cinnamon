package de.kiaim.cinnamon.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.serialization.mapper.JsonMapper;
import de.kiaim.cinnamon.model.serialization.mapper.YamlMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SerializationConfig {

//	@Bean
	public ObjectMapper jsonMapper() {
		return JsonMapper.jsonMapper();
	}

	@Bean
	public ObjectMapper yamlMapper() {
		return YamlMapper.yamlMapper();
	}
}
