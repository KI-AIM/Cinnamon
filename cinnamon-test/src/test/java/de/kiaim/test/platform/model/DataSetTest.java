package de.kiaim.test.platform.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.data.DataSet;
import de.kiaim.test.platform.ContextRequiredTest;
import de.kiaim.test.util.DataSetTestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataSetTest extends ContextRequiredTest {

	@Autowired
	ObjectMapper objectMapper;

	@Test
	public void serializationTest() throws JsonProcessingException {
		final DataSet dataSet = DataSetTestHelper.generateDataSet(true);
		final String json = objectMapper.writeValueAsString(dataSet);
		final String expected = DataSetTestHelper.generateDataSetAsYaml();
		assertEquals(expected, json);
	}

	@Test
	public void deserializationTest() throws JsonProcessingException {
		final String json = DataSetTestHelper.generateDataSetAsYaml();
		final DataSet dataSet = objectMapper.readValue(json, DataSet.class);
		final DataSet expected = DataSetTestHelper.generateDataSet(true);
		assertEquals(expected, dataSet);
	}
}
