package de.kiaim.cinnamon.platform.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.serialization.mapper.YamlMapper;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.TypeDescriptor;
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

	@Override
	public DataConfiguration convert(@NonNull final String value) {
		try {
			if (value.startsWith("{")) {
				return jsonMapper.readValue(value, DataConfiguration.class);
			} else {
				return yamlMapper.readValue(value, DataConfiguration.class);
			}
		} catch (final Exception e) {
			throw new ConversionFailedException(
					TypeDescriptor.valueOf(String.class),
					TypeDescriptor.valueOf(DataConfiguration.class),
					value,
					e);
		}
	}
}
