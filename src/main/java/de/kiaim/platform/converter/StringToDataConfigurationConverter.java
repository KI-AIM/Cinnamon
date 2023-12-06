package de.kiaim.platform.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToDataConfigurationConverter implements Converter<String, DataConfiguration> {

	private final ObjectMapper objectMapper;

	@Autowired
	public StringToDataConfigurationConverter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@SneakyThrows
	@Override
	public DataConfiguration convert(String value) {
		return objectMapper.readValue(value, DataConfiguration.class);
	}
}
