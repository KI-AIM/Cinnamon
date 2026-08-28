package de.kiaim.cinnamon.model.serialization;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import de.kiaim.cinnamon.model.data.Data;

public class DataSerializer extends ValueSerializer<Data> {
	@Override
	public void serialize(Data value, JsonGenerator gen, SerializationContext ctxt) {
		gen.writePOJO(value.getValue());
	}
}
