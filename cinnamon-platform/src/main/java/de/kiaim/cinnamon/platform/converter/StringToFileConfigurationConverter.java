package de.kiaim.cinnamon.platform.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.platform.model.file.FileConfiguration;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToFileConfigurationConverter implements Converter<String, FileConfiguration> {

	private final ObjectMapper objectMapper;

	@Autowired
	public StringToFileConfigurationConverter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@SneakyThrows
	@Override
	public FileConfiguration convert(String value) {
		return objectMapper.readValue(value, FileConfiguration.class);
	}
}
