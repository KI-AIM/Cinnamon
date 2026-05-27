package de.kiaim.cinnamon.platform.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.Nullable;

/**
 * Base class for converting JSON and YAML strings from multipart/form-data requests.
 *
 * @param <T> The target type.
 * @author Daniel Preciado-Marquez
 */
public abstract class CinnamonStringConverter<T> implements Converter<String, T> {

	private final Class<T> targetType;

	private final ObjectMapper jsonMapper;
	private final ObjectMapper yamlMapper;

	/**
	 * Constructor.
	 *
	 * @param targetType          The target type.
	 * @param serializationConfig The serialization configuration defining the JSON and YAML mappers.
	 */
	public CinnamonStringConverter(final Class<T> targetType, final SerializationConfig serializationConfig) {
		this.targetType = targetType;
		jsonMapper = serializationConfig.jsonMapper();
		yamlMapper = serializationConfig.yamlMapper();
	}

	/**
	 * Converts the given JSON or YAML string (never {@code null}) to target type {@code T}.
	 * Throws a {@link ConversionFailedException} if the conversion fails.
	 *
	 * @param source The source JSON or YAML string.
	 * @return The converted object.
	 */
	@Nullable @Override
	public T convert(@NonNull final String source) {
		try {
			if (source.startsWith("{")) {
				return jsonMapper.readValue(source, targetType);
			} else {
				return yamlMapper.readValue(source, targetType);
			}

		} catch (final JsonProcessingException e) {
			throw new ConversionFailedException(
					TypeDescriptor.valueOf(String.class),
					TypeDescriptor.valueOf(targetType),
					source,
					e);
		}
	}
}
