package de.kiaim.cinnamon.model.serialization.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

public abstract class JsonMapper {

	public static ObjectMapper jsonMapper() {
		var jsonMapper = new ObjectMapper();
		jsonMapper.registerModule(new JavaTimeModule());
		jsonMapper.registerModule(new ParameterNamesModule());
		jsonMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return jsonMapper;
	}

	public static String toYaml(String jsonString) throws JsonProcessingException {
		Object obj = jsonMapper().readValue(jsonString, Object.class);

		ObjectMapper yamlMapper = YamlMapper.yamlMapper();
		return yamlMapper.writeValueAsString(obj);
	}

}
