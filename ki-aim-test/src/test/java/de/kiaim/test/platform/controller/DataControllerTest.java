package de.kiaim.test.platform.controller;

import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.configuration.data.StringPatternConfiguration;
import de.kiaim.model.spring.CustomMediaType;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.entity.DataSetEntity;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.model.enumeration.Mode;
import de.kiaim.platform.model.enumeration.RowSelector;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.model.file.FileConfiguration;
import de.kiaim.platform.repository.DataSetRepository;
import de.kiaim.platform.service.ProjectService;
import de.kiaim.test.platform.ControllerTest;
import de.kiaim.test.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.oneOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithUserDetails("test_user")
class DataControllerTest extends ControllerTest {

	@Autowired ProjectService projectService;

	@Autowired DataSetRepository dataSetRepository;

	@BeforeEach
	public void setUp() {
		projectService.setMode(testProject, Mode.EXPERT);
	}

	@Test
	void estimateDatatypes() throws Exception {
		MockMultipartFile file = ResourceHelper.loadCsvFile();
		FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration();

		final String result = mockMvc.perform(multipart("/api/data/datatypes")
				                                      .file(file)
				                                      .param("fileConfiguration",
				                                             objectMapper.writeValueAsString(fileConfiguration)))
		                             .andExpect(status().isOk())
		                             .andReturn().getResponse().getContentAsString();

		final DataConfiguration dataConfiguration = objectMapper.readValue(result, DataConfiguration.class);

		final DataConfiguration expectedConfiguration = DataConfigurationTestHelper.generateEstimatedConfiguration();

		assertEquals(expectedConfiguration, dataConfiguration, "Returned configuration is wrong!");

		assertEquals(Step.UPLOAD, testProject.getStatus().getCurrentStep(),
		             "The current step should not have been updated!");
	}

	@Test
	void estimateDatatypesMissingFile() throws Exception {
		FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration();

		mockMvc.perform(multipart("/api/data/datatypes")
				                .param("fileConfiguration",
				                       objectMapper.writeValueAsString(fileConfiguration)))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("Request validation failed"))
		       .andExpect(validationError("file", "Data must be present!"));
	}

	@Test
	void estimateDatatypesMissingFileName() throws Exception {
		ClassLoader classLoader = getClass().getClassLoader();
		MockMultipartFile file = new MockMultipartFile("file", null, null,
		                                               classLoader.getResourceAsStream("test.csv"));
		FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration();

		mockMvc.perform(multipart("/api/data/datatypes")
				                .file(file)
				                .param("fileConfiguration", objectMapper.writeValueAsString(fileConfiguration)))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("Missing filename"));
	}

	@Test
	void estimateDatatypesMissingFileExtension() throws Exception {
		ClassLoader classLoader = getClass().getClassLoader();
		MockMultipartFile file = new MockMultipartFile("file", "file", null,
		                                               classLoader.getResourceAsStream("test.csv"));
		FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration();

		mockMvc.perform(multipart("/api/data/datatypes")
				                .file(file)
				                .param("fileConfiguration", objectMapper.writeValueAsString(fileConfiguration)))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("Missing file extension"));
	}

	@Test
	void estimateDatatypesMissingFileConfiguration() throws Exception {
		ClassLoader classLoader = getClass().getClassLoader();
		MockMultipartFile file = new MockMultipartFile("file", "file", null,
		                                               classLoader.getResourceAsStream("test.csv"));

		mockMvc.perform(multipart("/api/data/datatypes")
				                .file(file))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("Request validation failed"))
		       .andExpect(validationError("fileConfiguration", "File Configuration must be present!"));
	}

	@Test
	void readAndValidateData() throws Exception {
		MockMultipartFile file = ResourceHelper.loadCsvFile();
		FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration();
		final DataConfiguration configuration = DataConfigurationTestHelper.generateDataConfiguration();
		final TransformationResult expected = TransformationResultTestHelper.generateTransformationResult(false);

		mockMvc.perform(multipart("/api/data/validation")
				                .file(file)
				                .accept(CustomMediaType.APPLICATION_YAML)
				                .param("fileConfiguration", objectMapper.writeValueAsString(fileConfiguration))
				                .param("configuration", objectMapper.writeValueAsString(configuration)))
		       .andExpect(status().isOk())
		       .andExpect(content().string(objectMapper.writeValueAsString(expected)));

		assertEquals(Step.UPLOAD, testProject.getStatus().getCurrentStep(),
		             "The current step should have not been updated!");
	}

	@Test
	void readAndValidateDataMissingFile() throws Exception {
		FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration();
		final DataConfiguration configuration = DataConfigurationTestHelper.generateDataConfiguration();

		mockMvc.perform(multipart("/api/data/validation")
				                .param("fileConfiguration", objectMapper.writeValueAsString(fileConfiguration))
				                .param("configuration", objectMapper.writeValueAsString(configuration)))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("Request validation failed"))
		       .andExpect(validationError("file", "Data must be present!"));
	}

	@Test
	void readAndValidateDataMissingFileConfiguration() throws Exception {
		MockMultipartFile file = ResourceHelper.loadCsvFile();
		final DataConfiguration configuration = DataConfigurationTestHelper.generateDataConfiguration();

		mockMvc.perform(multipart("/api/data/validation")
				                .file(file)
				                .param("configuration", objectMapper.writeValueAsString(configuration)))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("Request validation failed"))
		       .andExpect(validationError("fileConfiguration", "File Configuration must be present!"));
	}

	@Test
	void readAndValidateDataMissingConfiguration() throws Exception {
		MockMultipartFile file = ResourceHelper.loadCsvFile();
		FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration();

		mockMvc.perform(multipart("/api/data/validation")
				                .file(file)
				                .param("fileConfiguration", objectMapper.writeValueAsString(fileConfiguration)))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("Request validation failed"))
		       .andExpect(validationError("configuration", "Configuration must be present!"));
	}

	@Test
	void readAndValidateDataInvalidConfiguration() throws Exception {
		MockMultipartFile file = ResourceHelper.loadCsvFile();
		FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration();

		mockMvc.perform(multipart("/api/data/validation")
				                .file(file)
				                .param("fileConfiguration", objectMapper.writeValueAsString(fileConfiguration))
				                .param("configuration", "invalid"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("Request validation failed"));

		mockMvc.perform(multipart("/api/data/validation")
				                .file(file)
				                .param("fileConfiguration", objectMapper.writeValueAsString(fileConfiguration))
				                .param("configuration", "\"invalid\""))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("Request validation failed"))
		       .andExpect(validationError("configuration", "Failed to convert value"));
	}

	@Test
	void storeConfigJson() throws Exception {
		final String jsonConfiguration = DataConfigurationTestHelper.generateDataConfigurationAsJson();
		testStoreConfig(jsonConfiguration);
	}

	@Test
	void storeConfigYaml() throws Exception {
		final String yamlConfiguration = DataConfigurationTestHelper.generateDataConfigurationAsYaml();
		testStoreConfig(yamlConfiguration);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void storeDataAndDeleteData() throws Exception {
		MockMultipartFile file = ResourceHelper.loadCsvFile();
		FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration();
		final DataConfiguration configuration = DataConfigurationTestHelper.generateDataConfiguration();

		String result = mockMvc.perform(multipart("/api/data")
				                                .file(file)
				                                .param("fileConfiguration",
				                                       objectMapper.writeValueAsString(fileConfiguration))
				                                .param("configuration",
				                                       objectMapper.writeValueAsString(configuration)))
		                       .andExpect(status().isOk())
		                       .andReturn().getResponse().getContentAsString();

		final long dataSetId = assertDoesNotThrow(() -> Long.parseLong(result.trim()));
		final DataSetEntity dataSetEntity = dataSetRepository.findById(dataSetId).get();

		UserEntity testUser = getTestUser();

		assertTrue(existsTable(dataSetId), "Table could not be found!");
		assertEquals(2, countEntries(dataSetId), "Number of entries wrong!");
		assertTrue(existsDataSet(dataSetId), "Configuration has not been persisted!");
		assertNotNull(testUser.getProject(), "User has not been associated with the dataset!");
		assertEquals(dataSetId, dataSetEntity.getId(), "User has been associated with the wrong dataset!");
		assertTrue(dataSetEntity.isStoredData(), "Flag that the data is stored should be true!");
		// TODO fix when creating projects dynamically
//		assertEquals(Step.ANONYMIZATION, testUser.getProject().getStatus().getCurrentStep(),
//		             "The current step has not been updated!");

		mockMvc.perform(MockMvcRequestBuilders.delete("/api/data")
		                                      .contentType(MediaType.APPLICATION_JSON_VALUE))
		       .andExpect(status().isOk());

		assertFalse(existsTable(dataSetId), "Table should be deleted!");
	}

	@Test
	void storeDataAndUpdateConfig() throws Exception {
		MockMultipartFile file = ResourceHelper.loadCsvFile();
		FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration();
		final DataConfiguration configuration = DataConfigurationTestHelper.generateDataConfiguration();

		mockMvc.perform(multipart("/api/data")
				                .file(file)
				                .param("fileConfiguration",
				                       objectMapper.writeValueAsString(fileConfiguration))
				                .param("configuration",
				                       objectMapper.writeValueAsString(configuration)))
		       .andExpect(status().isOk());

		final DataConfiguration configurationUpdate = DataConfigurationTestHelper.generateDataConfiguration("[0-9]*");

		mockMvc.perform(multipart("/api/data/configuration")
				                .file(file)
				                .param("fileConfiguration",
				                       objectMapper.writeValueAsString(fileConfiguration))
				                .param("configuration",
				                       objectMapper.writeValueAsString(
						                       configurationUpdate)))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("The data has already been stored!"));
	}

	@Test
	void loadConfigYaml() throws Exception {
		postData();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/configuration").accept(CustomMediaType.APPLICATION_YAML))
		       .andExpect(status().isOk())
		       .andExpect(
				       content().string(DataConfigurationTestHelper.generateDataConfigurationAsYaml()));
	}

	@Test
	void loadConfigJson() throws Exception {
		postData();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/configuration").accept(MediaType.APPLICATION_JSON))
		       .andExpect(status().isOk())
		       .andExpect(content().string(DataConfigurationTestHelper.generateDataConfigurationAsJson()));
	}

	// ================================================================================================================
	// region loadData()
	// ================================================================================================================

	@Test
	void loadData() throws Exception {
		postData();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/validation/data")
		                                      .accept(CustomMediaType.APPLICATION_YAML))
		       .andExpect(status().isOk())
		       .andExpect(content().string(DataSetTestHelper.generateDataAsYaml()));
	}

	@Test
	void loadDataNoDataSet() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/validation/data"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage(
				       "The project '" + testProject.getId() + "' does not contain a data set for step 'VALIDATION'!"));
	}

	@WithAnonymousUser
	@Test
	void loadDataNoPermissions() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/validation/data"))
		       .andExpect(status().isUnauthorized());
	}

	@Test
	void loadDataColumns() throws Exception {
		postData();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/validation/data")
		                                      .accept(CustomMediaType.APPLICATION_YAML)
		                                      .param("columns", "column4_integer,column0_boolean"))
		       .andExpect(status().isOk())
		       .andExpect(content().string(DataSetTestHelper.generateDataColumnsAsYaml()));
	}

	@Test
	void loadDataInvalidColumns() throws Exception {
		postData();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/validation/data")
		                                      .param("columns", "invalid1,column4_integer,invalid2"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("Data set does not contain columns with names: 'invalid1', 'invalid2'"));
	}

	@Test
	void loadDataEncoding() throws Exception {
		postData(true);

		final String defaultNullEncoding = "N/A";
		final String formatErrorEncoding = ":(";

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/validation/data")
		                                      .accept(CustomMediaType.APPLICATION_YAML)
		                                      .param("defaultNullEncoding", defaultNullEncoding)
		                                      .param("formatErrorEncoding", formatErrorEncoding))
		       .andExpect(status().isOk())
		       .andExpect(content().string(DataSetTestHelper.generateDataAsYaml(wrapInQuotes(defaultNullEncoding),
		                                                                        wrapInQuotes(formatErrorEncoding))));
	}

	@Test
	void loadDataEncodingNull() throws Exception {
		postData(true);

		final String defaultNullEncoding = "N/A";

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/validation/data")
		                                      .accept(CustomMediaType.APPLICATION_YAML)
		                                      .param("defaultNullEncoding", defaultNullEncoding)
		                                      .param("formatErrorEncoding", "$null"))
		       .andExpect(status().isOk())
		       .andExpect(content().string(DataSetTestHelper.generateDataAsYaml(wrapInQuotes(defaultNullEncoding),
		                                                                        "null")));
	}

	@Test
	void loadDataEncodingValue() throws Exception {
		postData(true);

		final String defaultNullEncoding = "N/A";

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/validation/data")
		                                      .accept(CustomMediaType.APPLICATION_YAML)
		                                      .param("defaultNullEncoding", defaultNullEncoding)
		                                      .param("formatErrorEncoding", "$value"))
		       .andExpect(status().isOk())
		       .andExpect(content().string(DataSetTestHelper.generateDataAsYaml(wrapInQuotes(defaultNullEncoding),
		                                                                        wrapInQuotes("forty two"))));
	}

	// ================================================================================================================
	// endregion
	// ================================================================================================================

	// ================================================================================================================
	// region loadDataSet()
	// ================================================================================================================

	@Test
	void loadDataSet() throws Exception {
		postData();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/validation")
		                                      .accept(CustomMediaType.APPLICATION_YAML))
		       .andExpect(status().isOk())
		       .andExpect(content().string(DataSetTestHelper.generateDataSetAsYaml()));
	}

	@Test
	void loadDataSetNoDataSet() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/validation"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage(
				       "The project '" + testProject.getId() + "' does not contain a data set for step 'VALIDATION'!"));
	}

	@WithAnonymousUser
	@Test
	void loadDataSetNoPermissions() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/validation"))
		       .andExpect(status().isUnauthorized());
	}

	@Test
	void loadDataSetColumns() throws Exception {
		postData();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/validation")
		                                      .accept(CustomMediaType.APPLICATION_YAML)
		                                      .param("columns", "column4_integer,column0_boolean"))
		       .andExpect(status().isOk())
		       .andExpect(content().string(DataSetTestHelper.generateDataSetColumnsAsYaml()));
	}

	@Test
	void loadDataSetInvalidColumns() throws Exception {
		postData();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/validation")
		                                      .param("columns", "invalid1,column4_integer,invalid2"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("Data set does not contain columns with names: 'invalid1', 'invalid2'"));
	}

	@Test
	void loadDataSetEncoding() throws Exception {
		postData(true);

		final String defaultNullEncoding = "N/A";
		final String formatErrorEncoding = ":(";

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/validation")
		                                      .accept(CustomMediaType.APPLICATION_YAML)
		                                      .param("defaultNullEncoding", defaultNullEncoding)
		                                      .param("formatErrorEncoding", formatErrorEncoding))
		       .andExpect(status().isOk())
		       .andExpect(
				       content().string(DataSetTestHelper.generateDataSetAsYaml(wrapInQuotes(defaultNullEncoding),
				                                                                wrapInQuotes(formatErrorEncoding))));
	}

	// ================================================================================================================
	// endregion loadDataSet()
	// ================================================================================================================

	@Test
	void loadTransformationResult() throws Exception {
		postData(true);

		mockMvc.perform(get("/api/data/validation/transformationResult"))
		       .andExpect(status().isOk())
		       .andExpect(content().string(oneOf(TransformationResultTestHelper.generateTransformationResultAsJsonA(),
		                                         TransformationResultTestHelper.generateTransformationResultAsJsonB())));
	}

	@Test
	void loadTransformationResultPage() throws Exception {
		postData();

		mockMvc.perform(get("/api/data/validation/transformationResult/page")
				                .param("page", "2")
				                .param("perPage", "1"))
		       .andExpect(status().isOk())
		       .andExpect(content().json(
				       "{'data':[[false,'2023-11-20','2023-11-20T12:50:27.123456',2.4,24,'Bye World!']],'transformationErrors':[],'rowNumbers':null,'page':2,'perPage':1,total:3,'totalPages':3}"));
	}

	@Test
	void loadTransformationResultPageWithErrors() throws Exception {
		postData();

		mockMvc.perform(get("/api/data/validation/transformationResult/page")
				                .param("page", "2")
				                .param("perPage", "2"))
		       .andExpect(status().isOk())
		       .andExpect(content().json(
				       "{'data':[[true,'2023-11-20',null,4.2,null,'Hello World!']],'transformationErrors':[{'index':0,'rawValues':['true','2023-11-20','','4.2','forty two','Hello World!'],'dataTransformationErrors':[{'index':2,'errorType':'MISSING_VALUE'},{'index':4,'errorType':'FORMAT_ERROR'}]}],'rowNumbers':null,'page':2,'perPage':2,total:3,'totalPages':2}"));
	}

	@Test
	void loadTransformationResultPageEncodedErrors() throws Exception {
		postData();

		mockMvc.perform(get("/api/data/validation/transformationResult/page")
				                .param("page", "3")
				                .param("perPage", "1")
				                .param("formatErrorEncoding", "$value"))
		       .andExpect(status().isOk())
		       .andExpect(content().json(
				       "{'data':[[true,'2023-11-20',null,4.2,'forty two','Hello World!']],'transformationErrors':[{'index':0,'rawValues':['true','2023-11-20','','4.2','forty two','Hello World!'],'dataTransformationErrors':[{'index':2,'errorType':'MISSING_VALUE'},{'index':4,'errorType':'FORMAT_ERROR'}]}],'rowNumbers':null,'page':3,'perPage':1,total:3,'totalPages':3}"));
	}

	@Test
	void loadTransformationResultPageSelectErrors() throws Exception {
		postData();

		mockMvc.perform(get("/api/data/validation/transformationResult/page")
				                .param("page", "1")
				                .param("perPage", "2")
				                .param("rowSelector", RowSelector.ERRORS.name())
				                .param("formatErrorEncoding", "$value"))
		       .andExpect(status().isOk())
		       .andExpect(content().json(
				       "{'data':[[true,'2023-11-20',null,4.2,'forty two','Hello World!']],'transformationErrors':[{'index':0,'rawValues':['true','2023-11-20','','4.2','forty two','Hello World!'],'dataTransformationErrors':[{'index':2,'errorType':'MISSING_VALUE'},{'index':4,'errorType':'FORMAT_ERROR'}]}],'rowNumbers':[2],'page':1,'perPage':2,total:1,'totalPages':1}"));
	}

	@Test
	void loadTransformationResultPageSelectValid() throws Exception {
		postData();

		mockMvc.perform(get("/api/data/validation/transformationResult/page")
				                .param("page", "1")
				                .param("perPage", "1")
				                .param("rowSelector", RowSelector.VALID.name())
				                .param("formatErrorEncoding", "$value"))
		       .andExpect(status().isOk())
		       .andExpect(content().json(
				       "{'data':[[true,'2023-11-20','2023-11-20T12:50:27.123456',4.2,42,'Hello World!']],'transformationErrors':[],'rowNumbers':[0],'page':1,'perPage':1,total:2,'totalPages':2}"));
	}


	@Test
	void deleteDataNoDataSet() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.delete("/api/data")
		                                      .contentType(MediaType.APPLICATION_JSON_VALUE))
		       .andExpect(status().isOk());
	}

	private String wrapInQuotes(final String value) {
		return "\"" + value + "\"";
	}

	private void testStoreConfig(final String configuration) throws Exception {
		MockMultipartFile file = ResourceHelper.loadCsvFile();
		FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration();

		mockMvc.perform(multipart("/api/data/configuration")
				                .file(file)
				                .param("fileConfiguration",
				                       objectMapper.writeValueAsString(fileConfiguration))
				                .param("configuration", configuration))
		       .andExpect(status().isOk());
		final DataSetEntity dataSetEntity = getTestProject().getDataSets().get(Step.VALIDATION);
		final var dataSetId = dataSetEntity.getId();

		UserEntity testUser = getTestUser();
		assertFalse(existsTable(dataSetId), "Table should not exist!");
		assertTrue(existsDataSet(dataSetId), "Configuration has not been persisted!");
		assertNotNull(testUser.getProject(), "User has not been associated with the dataset!");
		assertEquals(".*",
		             ((StringPatternConfiguration) testUser.getProject().getDataSets().get(Step.VALIDATION)
		                                                   .getDataConfiguration().getConfigurations().get(5)
		                                                   .getConfigurations().get(0))
				             .getPattern(),
		             "Type of first column does not match!");

		final DataConfiguration configurationUpdate = DataConfigurationTestHelper.generateDataConfiguration("[0-9]*");

		mockMvc.perform(multipart("/api/data/configuration")
				                .file(file)
				                .param("fileConfiguration",
				                       objectMapper.writeValueAsString(fileConfiguration))
				                .param("configuration",
				                       objectMapper.writeValueAsString(
						                       configurationUpdate)))
		       .andExpect(status().isOk())
		       .andReturn().getResponse().getContentAsString();

		testUser = getTestUser();
		assertFalse(existsTable(dataSetId), "Table should not exist!");
		assertTrue(existsDataSet(dataSetId), "Configuration has not been persisted!");
		assertNotNull(testUser.getProject(), "User has not been associated with the dataset!");
		assertEquals("[0-9]*",
		             ((StringPatternConfiguration) testUser.getProject().getDataSets().get(Step.VALIDATION)
		                                                   .getDataConfiguration().getConfigurations().get(5)
		                                                   .getConfigurations().get(0)).getPattern(),
		             "Type of first column does not match!");
	}

}
