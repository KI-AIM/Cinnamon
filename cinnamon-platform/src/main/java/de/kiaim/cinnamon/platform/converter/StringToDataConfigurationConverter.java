package de.kiaim.cinnamon.platform.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.serialization.mapper.YamlMapper;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToDataConfigurationConverter implements Converter<String, DataConfiguration> {

	private final ObjectMapper jsonMapper;
	private final ObjectMapper yamlMapper;

	@Autowired
	public StringToDataConfigurationConverter(final ObjectMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
		this.yamlMapper = YamlMapper.yamlMapper();
	}

	@SneakyThrows
	@Override
	public DataConfiguration convert(final String value) {
		if (value.startsWith("{")) {
			return jsonMapper.readValue(value, DataConfiguration.class);
		} else {
			return yamlMapper.readValue(value, DataConfiguration.class);
		}
	}
}
