package de.kiaim.cinnamon.model.serialization.mapper;

import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

/**
 * Wrapper class for the Object mapper configured for YAML.
 * Not implemented as a bean because Spring makes it incredibly difficult to have two ObjectMapper beans.
 */
public abstract class CinnamonYamlMapper {

	public static YAMLMapper yamlMapper() {
		return YAMLMapper.builder()
		                 .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
		                 .disable(YAMLWriteFeature.USE_NATIVE_TYPE_ID)
		                 .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
		                 .build();
	}

	public static String toJson(String yamlString) {
		Object obj = yamlMapper().readValue(yamlString, Object.class);

		JsonMapper jsonMapper = CinnamonJsonMapper.jsonMapper();
		return jsonMapper.writeValueAsString(obj);
	}
}
