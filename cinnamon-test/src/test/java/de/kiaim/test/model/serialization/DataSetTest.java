package de.kiaim.test.model.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.data.DataSet;
import de.kiaim.model.serialization.mapper.JsonMapper;
import de.kiaim.model.serialization.mapper.YamlMapper;
import de.kiaim.test.util.DataSetTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataSetTest {

	static ObjectMapper jsonMapper;
	static ObjectMapper yamlMapper;

	@BeforeAll
	static void beforeAll() {
		jsonMapper = JsonMapper.jsonMapper();
		yamlMapper = YamlMapper.yamlMapper();
	}

	@Test
	public void serializeJson() throws JsonProcessingException {
		final DataSet dataSet = DataSetTestHelper.generateDataSet(true);
		final String json = jsonMapper.writeValueAsString(dataSet);
		final String expected = DataSetTestHelper.generateDataSetAsJson();
		assertEquals(expected, json);
	}

	@Test
	public void serializeYaml() throws JsonProcessingException {
		final DataSet dataSet = DataSetTestHelper.generateDataSet(true);
		final String yaml = yamlMapper.writeValueAsString(dataSet);
		final String expected = DataSetTestHelper.generateDataSetAsYaml();
		assertEquals(expected, yaml);
	}

	@Test
	public void deserializeJson() throws JsonProcessingException {
		final String json = DataSetTestHelper.generateDataSetAsJson();
		final DataSet dataSet = jsonMapper.readValue(json, DataSet.class);
		final DataSet expected = DataSetTestHelper.generateDataSet(true);
		assertEquals(expected, dataSet);
	}

	@Test
	public void deserializeYaml() throws JsonProcessingException {
		final String yaml = DataSetTestHelper.generateDataSetAsYaml();
		final DataSet dataSet = yamlMapper.readValue(yaml, DataSet.class);
		final DataSet expected = DataSetTestHelper.generateDataSet(true);
		assertEquals(expected, dataSet);
	}
}
