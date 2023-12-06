package de.kiaim.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.platform.DatabaseTest;
import de.kiaim.platform.TestModelHelper;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class DataControllerTest extends DatabaseTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void estimateDatatypes() throws Exception {
		ClassLoader classLoader = getClass().getClassLoader();
		MockMultipartFile file = new MockMultipartFile("file", "data.csv", null,
		                                               classLoader.getResourceAsStream("test.csv"));

		final String result = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/data/datatypes").file(file))
		                             .andExpect(status().isOk())
		                             .andReturn().getResponse().getContentAsString();

		final DataConfiguration dataConfiguration = objectMapper.readValue(result, DataConfiguration.class);

		final DataConfiguration expectedConfiguration = TestModelHelper.generateEstimatedConfiguration();

		assertEquals(expectedConfiguration, dataConfiguration, "Returned configuration is wrong!");
	}

	@Test
	void estimateDatatypesMissingFile() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/data/datatypes"))
		       .andExpect(status().isBadRequest())
		       .andExpect(content().string("Missing part: 'file'"));
	}

	@Test
	void estimateDatatypesMissingFileName() throws Exception {
		ClassLoader classLoader = getClass().getClassLoader();
		MockMultipartFile file = new MockMultipartFile("file", null, null,
		                                               classLoader.getResourceAsStream("test.csv"));

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/data/datatypes").file(file))
		       .andExpect(status().isBadRequest())
		       .andExpect(content().string("Missing filename"));
	}

	@Test
	void estimateDatatypesMissingFileExtension() throws Exception {
		ClassLoader classLoader = getClass().getClassLoader();
		MockMultipartFile file = new MockMultipartFile("file", "file", null,
		                                               classLoader.getResourceAsStream("test.csv"));

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/data/datatypes").file(file))
		       .andExpect(status().isBadRequest())
		       .andExpect(content().string("Missing file extension"));
	}

	@Test
	void readAndValidateData() throws Exception {
		MockMultipartFile file = TestModelHelper.loadCsvFile();
		final DataConfiguration configuration = TestModelHelper.generateDataConfiguration();
		final TransformationResult expected = TestModelHelper.generateTransformationResult(false);

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/data/validation")
		                                      .file(file)
		                                      .param("configuration", objectMapper.writeValueAsString(configuration)))
		       .andExpect(status().isOk())
		       .andExpect(content().string(objectMapper.writeValueAsString(expected)));
	}

	@Test
	void readAndValidateDataMissingConfiguration() throws Exception {
		MockMultipartFile file = TestModelHelper.loadCsvFile();

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/data/validation")
		                                      .file(file))
		       .andExpect(status().isBadRequest())
		       .andExpect(content().string("Missing parameter: 'configuration'"));
	}

	@Test
	void readAndValidateDataInvalidConfiguration() throws Exception {
		MockMultipartFile file = TestModelHelper.loadCsvFile();

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/data/validation")
		                                      .file(file)
		                                      .param("configuration", "invalid"))
		       .andExpect(status().isBadRequest())
		       .andExpect(content().string("Invalid parameter: 'configuration'"));
		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/data/validation")
		                                      .file(file)
		                                      .param("configuration", "\"invalid\""))
		       .andExpect(status().isBadRequest())
		       .andExpect(content().string("Invalid parameter: 'configuration'"));
	}

	@Test
	void storeDataAndDeleteData() throws Exception {
		MockMultipartFile file = TestModelHelper.loadCsvFile();
		final DataConfiguration configuration = TestModelHelper.generateDataConfiguration();

		String result = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/data")
		                                                      .file(file)
		                                                      .param("configuration",
		                                                             objectMapper.writeValueAsString(configuration)))
		                       .andExpect(status().isOk())
		                       .andReturn().getResponse().getContentAsString();

		final long dataSetId = assertDoesNotThrow(() -> Long.parseLong(result));
		assertEquals(2, dataSetId, "Wrong dataSetId!");

		assertTrue(existsTable(dataSetId), "Table could not be found!");
		assertEquals(2, countEntries(dataSetId), "Number of entries wrong!");
		assertTrue(existsDataConfigration(dataSetId), "Configuration has not been persisted!");

		mockMvc.perform(MockMvcRequestBuilders.delete("/api/data")
		                                      .contentType(MediaType.APPLICATION_JSON_VALUE)
		                                      .param("dataSetId", String.valueOf(dataSetId)))
		       .andExpect(status().isOk());

		assertFalse(existsTable(dataSetId), "Table should be deleted!");
		assertFalse(existsDataConfigration(dataSetId), "Configuration has not been deleted!");
	}

	@Test
	void loadConfig() throws Exception {
		MockMultipartFile file = TestModelHelper.loadCsvFile();
		final DataConfiguration configuration = TestModelHelper.generateDataConfiguration();

		String result = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/data")
		                                                      .file(file)
		                                                      .param("configuration",
		                                                             objectMapper.writeValueAsString(configuration)))
		                       .andExpect(status().isOk())
		                       .andReturn().getResponse().getContentAsString();

		final long dataSetId = assertDoesNotThrow(() -> Long.parseLong(result));

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/configuration")
		                                      .contentType(MediaType.APPLICATION_JSON_VALUE)
		                                      .param("dataSetId", String.valueOf(dataSetId)))
		       .andExpect(status().isOk())
		       .andExpect(content().string(objectMapper.writeValueAsString(TestModelHelper.generateDataConfiguration())));
	}

	@Test
	void loadData() throws Exception {
		MockMultipartFile file = TestModelHelper.loadCsvFile();
		final DataConfiguration configuration = TestModelHelper.generateDataConfiguration();

		String result = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/data")
		                                                      .file(file)
		                                                      .param("configuration",
		                                                             objectMapper.writeValueAsString(configuration)))
		                       .andExpect(status().isOk())
		                       .andReturn().getResponse().getContentAsString();

		final long dataSetId = assertDoesNotThrow(() -> Long.parseLong(result));

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/data")
		                                      .contentType(MediaType.APPLICATION_JSON_VALUE)
		                                      .param("dataSetId", String.valueOf(dataSetId)))
		       .andExpect(status().isOk())
		       .andExpect(
				       content().string(objectMapper.writeValueAsString(TestModelHelper.generateDataSet().getData())));
	}


	@Test
	void loadDataSet() throws Exception {
		MockMultipartFile file = TestModelHelper.loadCsvFile();
		final DataConfiguration configuration = TestModelHelper.generateDataConfiguration();

		String result = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/data")
		                                                      .file(file)
		                                                      .param("configuration",
		                                                             objectMapper.writeValueAsString(configuration)))
		                       .andExpect(status().isOk())
		                       .andReturn().getResponse().getContentAsString();

		final long dataSetId = assertDoesNotThrow(() -> Long.parseLong(result));

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data")
		                                      .contentType(MediaType.APPLICATION_JSON_VALUE)
		                                      .param("dataSetId", String.valueOf(dataSetId)))
		       .andExpect(status().isOk())
		       .andExpect(content().string(objectMapper.writeValueAsString(TestModelHelper.generateDataSet())));
	}

	@Test
	void loadDataSetMissingDataSetId() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/api/data")
		                                      .contentType(MediaType.APPLICATION_JSON_VALUE))
		       .andExpect(status().isBadRequest())
		       .andExpect(content().string("Missing parameter: 'dataSetId'"));
	}

	@Test
	void loadDataSetInvalidDataSetId() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/api/data")
		                                      .contentType(MediaType.APPLICATION_JSON_VALUE)
		                                      .param("dataSetId", "invalid"))
		       .andExpect(status().isBadRequest())
		       .andExpect(content().string("Invalid parameter: 'dataSetId'"));
	}

	@Test
	void loadDataSetWrongDataSetId() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/api/data")
		                                      .contentType(MediaType.APPLICATION_JSON_VALUE)
		                                      .param("dataSetId", String.valueOf(0L)))
		       .andExpect(status().isBadRequest())
		       .andExpect(content().string("No DataSet with the given ID '0' found!"));
	}

	@Test
	void deleteDataMissingDataSetId() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.delete("/api/data")
		                                      .contentType(MediaType.APPLICATION_JSON_VALUE))
		       .andExpect(status().isBadRequest())
		       .andExpect(content().string("Missing parameter: 'dataSetId'"));
	}

	@Test
	void deleteDataInvalidDataSetId() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.delete("/api/data")
		                                      .contentType(MediaType.APPLICATION_JSON_VALUE)
		                                      .param("dataSetId", "invalid"))
		       .andExpect(status().isBadRequest())
		       .andExpect(content().string("Invalid parameter: 'dataSetId'"));
	}

	@Test
	void deleteDataWrongDataSetId() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.delete("/api/data")
		                                      .contentType(MediaType.APPLICATION_JSON_VALUE)
		                                      .param("dataSetId", String.valueOf(0L)))
		       .andExpect(status().isBadRequest())
		       .andExpect(content().string("No DataSet with the given ID '0' found!"));
	}

}
