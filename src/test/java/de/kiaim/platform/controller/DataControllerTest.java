package de.kiaim.platform.controller;

import de.kiaim.platform.ControllerTest;
import de.kiaim.platform.TestModelHelper;
import de.kiaim.platform.model.file.FileConfiguration;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.model.data.configuration.StringPatternConfiguration;
import de.kiaim.platform.model.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithUserDetails("test_user")
class DataControllerTest extends ControllerTest {

	@Test
	void estimateDatatypes() throws Exception {
		MockMultipartFile file = TestModelHelper.loadCsvFile();
		FileConfiguration fileConfiguration = TestModelHelper.generateFileConfigurationCsv();

		final String result = mockMvc.perform(multipart("/api/data/datatypes")
				                                      .file(file)
				                                      .param("fileConfiguration",
				                                             objectMapper.writeValueAsString(fileConfiguration)))
		                             .andExpect(status().isOk())
		                             .andReturn().getResponse().getContentAsString();

		final DataConfiguration dataConfiguration = objectMapper.readValue(result, DataConfiguration.class);

		final DataConfiguration expectedConfiguration = TestModelHelper.generateEstimatedConfiguration();

		assertEquals(expectedConfiguration, dataConfiguration, "Returned configuration is wrong!");
	}

	@Test
	void estimateDatatypesMissingFile() throws Exception {
		String result = mockMvc.perform(multipart("/api/data/datatypes"))
		                       .andExpect(status().isBadRequest())
		                       .andReturn().getResponse().getContentAsString();

		testErrorMessage(result, "Missing part: 'file'");
	}

	@Test
	void estimateDatatypesMissingFileName() throws Exception {
		ClassLoader classLoader = getClass().getClassLoader();
		MockMultipartFile file = new MockMultipartFile("file", null, null,
		                                               classLoader.getResourceAsStream("test.csv"));
		FileConfiguration fileConfiguration = TestModelHelper.generateFileConfigurationCsv();

		String result = mockMvc.perform(multipart("/api/data/datatypes")
				                                .file(file)
				                                .param("fileConfiguration",
				                                       objectMapper.writeValueAsString(fileConfiguration)))
		                       .andExpect(status().isBadRequest())
		                       .andReturn().getResponse().getContentAsString();

		testErrorMessage(result, "Missing filename");
	}

	@Test
	void estimateDatatypesMissingFileExtension() throws Exception {
		ClassLoader classLoader = getClass().getClassLoader();
		MockMultipartFile file = new MockMultipartFile("file", "file", null,
		                                               classLoader.getResourceAsStream("test.csv"));
		FileConfiguration fileConfiguration = TestModelHelper.generateFileConfigurationCsv();

		String result = mockMvc.perform(multipart("/api/data/datatypes")
				                                .file(file)
				                                .param("fileConfiguration",
				                                       objectMapper.writeValueAsString(fileConfiguration)))
		                       .andExpect(status().isBadRequest())
		                       .andReturn().getResponse().getContentAsString();

		testErrorMessage(result, "Missing file extension");
	}

	@Test
	void estimateDatatypesMissingFileConfiguration() throws Exception {
		ClassLoader classLoader = getClass().getClassLoader();
		MockMultipartFile file = new MockMultipartFile("file", "file", null,
		                                               classLoader.getResourceAsStream("test.csv"));

		String result = mockMvc.perform(multipart("/api/data/datatypes")
				                                .file(file))
		                       .andExpect(status().isBadRequest())
		                       .andReturn().getResponse().getContentAsString();

		testErrorMessage(result, "Missing parameter: 'fileConfiguration'");
	}

	@Test
	void readAndValidateData() throws Exception {
		MockMultipartFile file = TestModelHelper.loadCsvFile();
		FileConfiguration fileConfiguration = TestModelHelper.generateFileConfigurationCsv();
		final DataConfiguration configuration = TestModelHelper.generateDataConfiguration();
		final TransformationResult expected = TestModelHelper.generateTransformationResult(false);

		mockMvc.perform(multipart("/api/data/validation")
				                .file(file)
				                .param("fileConfiguration", objectMapper.writeValueAsString(fileConfiguration))
				                .param("configuration", objectMapper.writeValueAsString(configuration)))
		       .andExpect(status().isOk())
		       .andExpect(content().string(objectMapper.writeValueAsString(expected)));
	}

	@Test
	void readAndValidateDataMissingConfiguration() throws Exception {
		MockMultipartFile file = TestModelHelper.loadCsvFile();
		FileConfiguration fileConfiguration = TestModelHelper.generateFileConfigurationCsv();

		String result = mockMvc.perform(multipart("/api/data/validation")
				                                .file(file)
				                                .param("fileConfiguration",
				                                       objectMapper.writeValueAsString(fileConfiguration)))
		                       .andExpect(status().isBadRequest())
		                       .andReturn().getResponse().getContentAsString();

		testErrorMessage(result, "Missing parameter: 'configuration'");
	}

	@Test
	void readAndValidateDataInvalidConfiguration() throws Exception {
		MockMultipartFile file = TestModelHelper.loadCsvFile();
		FileConfiguration fileConfiguration = TestModelHelper.generateFileConfigurationCsv();

		String result = mockMvc.perform(multipart("/api/data/validation")
				                                .file(file)
				                                .param("fileConfiguration",
				                                       objectMapper.writeValueAsString(fileConfiguration))
				                                .param("configuration", "invalid"))
		                       .andExpect(status().isBadRequest())
		                       .andReturn().getResponse().getContentAsString();
		testErrorMessage(result, "Invalid parameter: 'configuration'");

		result = mockMvc.perform(multipart("/api/data/validation")
				                         .file(file)
				                         .param("fileConfiguration",
				                                objectMapper.writeValueAsString(fileConfiguration))
				                         .param("configuration", "\"invalid\""))
		                .andExpect(status().isBadRequest())
		                .andReturn().getResponse().getContentAsString();

		testErrorMessage(result, "Invalid parameter: 'configuration'");
	}


	@Test
	@Transactional
	void storeConfig() throws Exception {
		final DataConfiguration configuration = TestModelHelper.generateDataConfiguration();

		final String result = mockMvc.perform(multipart("/api/data/configuration")
				                                      .param("configuration",
				                                             objectMapper.writeValueAsString(configuration)))
		                             .andExpect(status().isOk())
		                             .andReturn().getResponse().getContentAsString();

		final long dataSetId = assertDoesNotThrow(() -> Long.parseLong(result));

		UserEntity testUser = getTestUser();
		assertFalse(existsTable(dataSetId), "Table should not exist!");
		assertTrue(existsDataConfigration(dataSetId), "Configuration has not been persisted!");
		assertNotNull(testUser.getDataConfiguration(), "User has not been associated with the dataset!");
		assertEquals(dataSetId, testUser.getDataConfiguration().getId(), "User has been associated with the wrong dataset!");
		assertEquals(".*",
		             ((StringPatternConfiguration) testUser.getDataConfiguration().getDataConfiguration()
		                                                   .getConfigurations().get(5).getConfigurations()
		                                                   .get(0))
				             .getPattern(),
		             "Type of first column does not match!");

		final DataConfiguration configurationUpdate = TestModelHelper.generateDataConfiguration("[0-9]*");

		final String resultUpdate = mockMvc.perform(multipart("/api/data/configuration")
				                                            .param("configuration",
				                                                   objectMapper.writeValueAsString(
						                                                   configurationUpdate)))
		                                   .andExpect(status().isOk())
		                                   .andReturn().getResponse().getContentAsString();

		final long dataSetIdUpdate = assertDoesNotThrow(() -> Long.parseLong(resultUpdate));

		testUser = getTestUser();
		assertFalse(existsTable(dataSetId), "Table should not exist!");
		assertTrue(existsDataConfigration(dataSetId), "Configuration has not been persisted!");
		assertNotNull(testUser.getDataConfiguration(), "User has not been associated with the dataset!");
		assertEquals(dataSetIdUpdate, testUser.getDataConfiguration().getId(), "User has been associated with the wrong dataset!");
		assertEquals(dataSetIdUpdate, dataSetId, "Update has changed the DataSet id!");
		assertEquals("[0-9]*",
		             ((StringPatternConfiguration) testUser.getDataConfiguration().getDataConfiguration()
		                                                   .getConfigurations().get(5).getConfigurations()
		                                                   .get(0)).getPattern(),
		             "Type of first column does not match!");
	}

	@Test
	void storeDataAndDeleteData() throws Exception {
		MockMultipartFile file = TestModelHelper.loadCsvFile();
		FileConfiguration fileConfiguration = TestModelHelper.generateFileConfigurationCsv();
		final DataConfiguration configuration = TestModelHelper.generateDataConfiguration();

		String result = mockMvc.perform(multipart("/api/data")
				                                .file(file)
				                                .param("fileConfiguration",
				                                       objectMapper.writeValueAsString(fileConfiguration))
				                                .param("configuration",
				                                       objectMapper.writeValueAsString(configuration)))
		                       .andExpect(status().isOk())
		                       .andReturn().getResponse().getContentAsString();

		final long dataSetId = assertDoesNotThrow(() -> Long.parseLong(result));

		UserEntity testUser = getTestUser();

		assertTrue(existsTable(dataSetId), "Table could not be found!");
		assertEquals(2, countEntries(dataSetId), "Number of entries wrong!");
		assertTrue(existsDataConfigration(dataSetId), "Configuration has not been persisted!");
		assertNotNull(testUser.getDataConfiguration(), "User has not been associated with the dataset!");
		assertEquals(dataSetId, testUser.getDataConfiguration().getId(), "User has been associated with the wrong dataset!");

		mockMvc.perform(MockMvcRequestBuilders.delete("/api/data")
		                                      .contentType(MediaType.APPLICATION_JSON_VALUE))
		       .andExpect(status().isOk());

		assertFalse(existsTable(dataSetId), "Table should be deleted!");
		assertFalse(existsDataConfigration(dataSetId), "Configuration has not been deleted!");
		assertNull(getTestUser().getDataConfiguration(), "User association with the dataset has not been removed!");
	}

	@Test
	void storeDataAndUpdateConfig() throws Exception {
		MockMultipartFile file = TestModelHelper.loadCsvFile();
		FileConfiguration fileConfiguration = TestModelHelper.generateFileConfigurationCsv();
		final DataConfiguration configuration = TestModelHelper.generateDataConfiguration();

		mockMvc.perform(multipart("/api/data")
				                .file(file)
				                .param("fileConfiguration",
				                       objectMapper.writeValueAsString(fileConfiguration))
				                .param("configuration",
				                       objectMapper.writeValueAsString(configuration)))
		       .andExpect(status().isOk());

		final DataConfiguration configurationUpdate = TestModelHelper.generateDataConfiguration("[0-9]*");

		final String resultUpdate = mockMvc.perform(multipart("/api/data/configuration")
				                                            .param("configuration",
				                                                   objectMapper.writeValueAsString(
						                                                   configurationUpdate)))
		                                   .andExpect(status().isBadRequest())
		                                   .andReturn().getResponse().getContentAsString();

		testErrorMessage(resultUpdate, "The data has already been stored!");
	}

	@Test
	void loadConfig() throws Exception {
		postData();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/configuration"))
		       .andExpect(status().isOk())
		       .andExpect(
				       content().string(objectMapper.writeValueAsString(TestModelHelper.generateDataConfiguration())));
	}

	@Test
	void loadConfigYaml() throws Exception {
		postData();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/configuration")
		                                      .param("format", "yaml"))
		       .andExpect(status().isOk())
		       .andExpect(content().string(TestModelHelper.generateDataConfigurationAsYaml()));
	}

	@Test
	void loadData() throws Exception {
		MockMultipartFile file = TestModelHelper.loadCsvFile();
		FileConfiguration fileConfiguration = TestModelHelper.generateFileConfigurationCsv();
		final DataConfiguration configuration = TestModelHelper.generateDataConfiguration();

		String result = mockMvc.perform(multipart("/api/data")
				                                .file(file)
				                                .param("fileConfiguration",
				                                       objectMapper.writeValueAsString(fileConfiguration))
				                                .param("configuration",
				                                       objectMapper.writeValueAsString(configuration)))
		                       .andExpect(status().isOk())
		                       .andReturn().getResponse().getContentAsString();

		assertDoesNotThrow(() -> Long.parseLong(result));

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/data"))
		       .andExpect(status().isOk())
		       .andExpect(
				       content().string(objectMapper.writeValueAsString(TestModelHelper.generateDataSet().getData())));
	}


	@Test
	void loadDataSet() throws Exception {
		MockMultipartFile file = TestModelHelper.loadCsvFile();
		FileConfiguration fileConfiguration = TestModelHelper.generateFileConfigurationCsv();
		final DataConfiguration configuration = TestModelHelper.generateDataConfiguration();

		String result = mockMvc.perform(multipart("/api/data")
				                                .file(file)
				                                .param("fileConfiguration",
				                                       objectMapper.writeValueAsString(fileConfiguration))
				                                .param("configuration",
				                                       objectMapper.writeValueAsString(configuration)))
		                       .andExpect(status().isOk())
		                       .andReturn().getResponse().getContentAsString();

		assertDoesNotThrow(() -> Long.parseLong(result));

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data"))
		       .andExpect(status().isOk())
		       .andExpect(content().string(objectMapper.writeValueAsString(TestModelHelper.generateDataSet())));
	}

	@Test
	void loadDataSetNoDataSet() throws Exception {
		String result = mockMvc.perform(MockMvcRequestBuilders.get("/api/data"))
		                       .andExpect(status().isBadRequest())
		                       .andReturn().getResponse().getContentAsString();

		testErrorMessage(result, "User has no configuration!");
	}

	@WithAnonymousUser
	@Test
	void loadDataSetNoPermissions() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/api/data"))
		       .andExpect(status().isUnauthorized());
	}

	@Test
	void deleteDataNoDataSet() throws Exception {
		String result = mockMvc.perform(MockMvcRequestBuilders.delete("/api/data")
		                                                      .contentType(MediaType.APPLICATION_JSON_VALUE))
		                       .andExpect(status().isBadRequest())
		                       .andReturn().getResponse().getContentAsString();

		testErrorMessage(result, "User has no configuration!");
	}

	private void postData() throws Exception {
		MockMultipartFile file = TestModelHelper.loadCsvFile();
		FileConfiguration fileConfiguration = TestModelHelper.generateFileConfigurationCsv();
		final DataConfiguration configuration = TestModelHelper.generateDataConfiguration();

		String result = mockMvc.perform(multipart("/api/data")
				                                .file(file)
				                                .param("fileConfiguration",
				                                       objectMapper.writeValueAsString(fileConfiguration))
				                                .param("configuration",
				                                       objectMapper.writeValueAsString(configuration)))
		                       .andExpect(status().isOk())
		                       .andReturn().getResponse().getContentAsString();

		assertDoesNotThrow(() -> Long.parseLong(result));
	}

}
