package de.kiaim.model.serialization.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

/**
 * Wrapper class for the Object mapper configured for YAML.
 * Not implemented as a bean because Spring makes it incredibly difficult to have two ObjectMapper beans.
 */
public abstract class YamlMapper {

	public static ObjectMapper yamlMapper() {
		final YAMLFactory yamlFactory = new YAMLFactory();
		yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
		yamlFactory.disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID);

		final ObjectMapper yamlMapper = new ObjectMapper(yamlFactory);
		yamlMapper.registerModule(new JavaTimeModule());
		yamlMapper.registerModule(new ParameterNamesModule());
		yamlMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

		return yamlMapper;
	}

	public static String toJson(String yamlString) throws JsonProcessingException {
		Object obj = yamlMapper().readValue(yamlString, Object.class);

		ObjectMapper jsonMapper = JsonMapper.jsonMapper();
		return jsonMapper.writeValueAsString(obj);
	}
}
