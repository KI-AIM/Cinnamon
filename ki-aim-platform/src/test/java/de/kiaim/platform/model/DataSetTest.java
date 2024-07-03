package de.kiaim.platform.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.data.DataSet;
import de.kiaim.platform.ContextRequiredTest;
import de.kiaim.platform.TestModelHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataSetTest extends ContextRequiredTest {

	@Autowired
	ObjectMapper objectMapper;

	@Test
	public void serializationTest() throws JsonProcessingException {
		final DataSet dataSet = TestModelHelper.generateDataSet(true);
		final String json = objectMapper.writeValueAsString(dataSet);
		final String expected = TestModelHelper.generateDataSetAsYaml();
		assertEquals(expected, json);
	}

	@Test
	public void deserializationTest() throws JsonProcessingException {
		final String json = TestModelHelper.generateDataSetAsYaml();
		final DataSet dataSet = objectMapper.readValue(json, DataSet.class);
		final DataSet expected = TestModelHelper.generateDataSet(true);
		assertEquals(expected, dataSet);
	}
}
