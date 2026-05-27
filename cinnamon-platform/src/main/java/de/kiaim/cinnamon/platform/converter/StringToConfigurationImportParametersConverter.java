package de.kiaim.cinnamon.platform.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.dto.ConfigurationImportParameters;
import de.kiaim.cinnamon.model.serialization.mapper.JsonMapper;
import lombok.SneakyThrows;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converts a string to a {@link ConfigurationImportParameters} from form data of incoming requests.
 * Is automatically applied without additional configuration.
 *
 * @author Daniel Preciado-Marquez
 */
@Component
public class StringToConfigurationImportParametersConverter implements Converter<String, ConfigurationImportParameters> {

	private final ObjectMapper jsonMapper;

	public StringToConfigurationImportParametersConverter() {
		this.jsonMapper = JsonMapper.jsonMapper();
	}

	@Override
	@SneakyThrows
	@Nullable
	public ConfigurationImportParameters convert(final String source) {
		return jsonMapper.readValue(source, ConfigurationImportParameters.class);
	}
}
