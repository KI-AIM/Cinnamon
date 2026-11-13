package de.kiaim.cinnamon.platform.converter;

import de.kiaim.cinnamon.platform.config.SerializationConfig;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.hibernate.type.descriptor.java.MutableMutabilityPlan;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Hypersistence's JsonType uses Jackson 2; extraction uses Jackson 3 throughout.
@Converter
public class JsonNodeAttributeConverter implements AttributeConverter<JsonNode, String> {

	// Hibernate also copies detached values on merge, outside the attribute converter.
	public static class JsonNodeMutabilityPlan extends MutableMutabilityPlan<JsonNode> {
		@Override
		protected JsonNode deepCopyNotNull(final JsonNode value) {
			return value.deepCopy();
		}
	}

	private final JsonMapper jsonMapper;

	public JsonNodeAttributeConverter(final SerializationConfig serializationConfig) {
		this.jsonMapper = serializationConfig.jsonMapper();
	}

	@Override
	public @Nullable String convertToDatabaseColumn(final @Nullable JsonNode attribute) {
		return attribute == null ? null : jsonMapper.writeValueAsString(attribute);
	}

	@Override
	public @Nullable JsonNode convertToEntityAttribute(final @Nullable String dbData) {
		return dbData == null ? null : jsonMapper.readTree(dbData);
	}
}
