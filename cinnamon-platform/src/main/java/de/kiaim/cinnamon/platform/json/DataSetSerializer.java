package de.kiaim.cinnamon.platform.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.platform.service.DataSetService;

import java.io.IOException;
import java.util.List;

public class DataSetSerializer  extends JsonSerializer<DataSet> {

	private final DataSetService dataSetService;

	public DataSetSerializer(final DataSetService dataSetService) {
		this.dataSetService = dataSetService;
	}

	@Override
	public void serialize(DataSet value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
		gen.writeStartObject();

		gen.writeObjectField("dataConfiguration", value.getDataConfiguration());

		final List<List<Object>> data = dataSetService.encodeDataRows(value);
		gen.writeObjectField("data", data);

		gen.writeEndObject();
	}
}
