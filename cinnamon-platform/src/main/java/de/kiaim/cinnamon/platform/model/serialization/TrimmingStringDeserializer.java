package de.kiaim.cinnamon.platform.model.serialization;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdScalarDeserializer;

/**
 * Custom deserializer that trims leading and trailing whitespace from strings during deserialization.
 * Null values are preserved and not converted to empty strings.
 *
 * @author Daniel Preciado-Marquez
 */
public class TrimmingStringDeserializer extends StdScalarDeserializer<String> {
	public TrimmingStringDeserializer() {
		super(String.class);
	}

	@Override
	public String deserialize(final JsonParser p, final DeserializationContext ctxt) {
		final String value = p.getValueAsString();
		return value != null ? value.trim() : null;
	}
}
