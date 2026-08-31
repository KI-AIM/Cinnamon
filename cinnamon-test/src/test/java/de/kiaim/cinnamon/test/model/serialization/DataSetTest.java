package de.kiaim.cinnamon.test.model.serialization;

import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.model.serialization.mapper.CinnamonJsonMapper;
import de.kiaim.cinnamon.model.serialization.mapper.CinnamonYamlMapper;
import de.kiaim.cinnamon.test.util.DataSetTestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataSetTest {

	static JsonMapper jsonMapper;
	static YAMLMapper yamlMapper;

	@BeforeAll
	static void beforeAll() {
		jsonMapper = CinnamonJsonMapper.jsonMapper();
		yamlMapper = CinnamonYamlMapper.yamlMapper();
	}

	@Test
	public void serializeJson() {
		final DataSet dataSet = DataSetTestHelper.generateDataSet(true);
		final String json = jsonMapper.writeValueAsString(dataSet);
		final String expected = DataSetTestHelper.generateDataSetAsJson();
		assertEquals(expected, json);
	}

	@Test
	public void serializeYaml() {
		final DataSet dataSet = DataSetTestHelper.generateDataSet(true);
		final String yaml = yamlMapper.writeValueAsString(dataSet);
		final String expected = DataSetTestHelper.generateDataSetAsYaml();
		assertEquals(expected, yaml);
	}

	@Test
	public void deserializeJson() {
		final String json = DataSetTestHelper.generateDataSetAsJson();
		final DataSet dataSet = jsonMapper.readValue(json, DataSet.class);
		final DataSet expected = DataSetTestHelper.generateDataSet(true);
		assertEquals(expected, dataSet);
	}

	@Test
	public void deserializeYaml() {
		final String yaml = DataSetTestHelper.generateDataSetAsYaml();
		final DataSet dataSet = yamlMapper.readValue(yaml, DataSet.class);
		final DataSet expected = DataSetTestHelper.generateDataSet(true);
		assertEquals(expected, dataSet);
	}
}
