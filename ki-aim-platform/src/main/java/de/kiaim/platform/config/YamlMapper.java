package de.kiaim.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;

/**
 * Wrapper class for the Object mapper configured for YAML.
 * Not implemented as a bean because Spring makes it incredibly difficult to have two ObjectMapper beans.
 */
@Getter
public abstract class YamlMapper {

	private static ObjectMapper yamlMapper = null;

	public static ObjectMapper yamlMapper() {
		if (yamlMapper == null) {
			final YAMLFactory yamlFactory = new YAMLFactory();
			yamlFactory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
			yamlFactory.disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID);

			yamlMapper = new ObjectMapper(yamlFactory);
			yamlMapper.registerModule(new JavaTimeModule());
			yamlMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		}

		return yamlMapper;
	}
}
