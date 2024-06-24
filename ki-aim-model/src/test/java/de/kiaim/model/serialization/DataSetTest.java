package de.kiaim.model.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.DataSetTestHelper;
import de.kiaim.model.configuration.data.*;
import de.kiaim.model.data.*;
import de.kiaim.model.enumeration.DataScale;
import de.kiaim.model.enumeration.DataType;
import de.kiaim.model.serialization.mapper.JsonMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataSetTest {

	static ObjectMapper jsonMapper;

	@BeforeAll
	static void beforeAll() {
		jsonMapper = JsonMapper.jsonMapper();
	}

	@Test
	public void deserializationTest() throws JsonProcessingException {
		final String json = DataSetTestHelper.generateDataSetAsJson();
		final DataSet dataSet = jsonMapper.readValue(json, DataSet.class);
		final DataSet expected = DataSetTestHelper.generateDataSet(true);
		assertEquals(expected, dataSet);
	}

	@Test
	public void serializationTest() throws JsonProcessingException {
		final DataSet dataSet = DataSetTestHelper.generateDataSet(true);
		final String json = jsonMapper.writeValueAsString(dataSet);
		final String expected = DataSetTestHelper.generateDataSetAsJson();
		assertEquals(expected, json);
	}
}
