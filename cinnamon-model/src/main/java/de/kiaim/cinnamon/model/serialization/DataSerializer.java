package de.kiaim.cinnamon.model.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import de.kiaim.cinnamon.model.data.Data;

import java.io.IOException;

public class DataSerializer extends JsonSerializer<Data> {
	@Override
	public void serialize(Data value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
		gen.writeObject(value.getValue());
	}
}
