package de.kiaim.cinnamon.platform.converter;

import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.lang.Nullable;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converts a {@link DataConfiguration} into its JSON representation for persistance and back.
 * <p>
 * A generic Hibernate {@code @Type(JsonType.class)} cannot be used here because it relies on its own,
 * independently configured Jackson 2 {@code ObjectMapper} that knows nothing about the Jackson 3 based
 * (de-)serializers used throughout the {@link DataConfiguration} class hierarchy (e.g. {@link de.kiaim.cinnamon.model.data.Data}).
 * This converter therefore uses the same {@link JsonMapper} that is used for the rest of the application.
 *
 * @author Daniel Preciado-Marquez
 */
@Converter
public class DataConfigurationAttributeConverter implements AttributeConverter<DataConfiguration, String> {

	private final JsonMapper jsonMapper;

	public DataConfigurationAttributeConverter(final SerializationConfig serializationConfig) {
		this.jsonMapper = serializationConfig.jsonMapper();
	}

	@Nullable
	@Override
	public String convertToDatabaseColumn(@Nullable final DataConfiguration attribute) {
		if (attribute == null) {
			return null;
		}

		return jsonMapper.writeValueAsString(attribute);
	}

	@Nullable
	@Override
	public DataConfiguration convertToEntityAttribute(@Nullable final String dbData) {
		if (dbData == null) {
			return null;
		}

		return jsonMapper.readValue(dbData, DataConfiguration.class);
	}
}
