package de.kiaim.cinnamon.model.serialization.mapper;

import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

public abstract class CinnamonJsonMapper {

	public static JsonMapper jsonMapper() {
		return JsonMapper.builder()
		                 .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
		                 .build();
	}

	public static String toYaml(String jsonString) {
		Object obj = jsonMapper().readValue(jsonString, Object.class);

		YAMLMapper yamlMapper = CinnamonYamlMapper.yamlMapper();
		return yamlMapper.writeValueAsString(obj);
	}

}
