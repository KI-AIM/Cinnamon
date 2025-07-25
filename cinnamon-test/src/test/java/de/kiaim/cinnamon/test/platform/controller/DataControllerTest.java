package de.kiaim.cinnamon.test.platform.controller;

import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.configuration.data.StringPatternConfiguration;
import de.kiaim.cinnamon.model.enumeration.DataType;
import de.kiaim.cinnamon.model.spring.CustomMediaType;
import de.kiaim.cinnamon.platform.exception.ApiException;
import de.kiaim.cinnamon.platform.model.dto.DataConfigurationEstimation;
import de.kiaim.cinnamon.platform.model.entity.DataSetEntity;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.model.enumeration.HoldOutSelector;
import de.kiaim.cinnamon.platform.model.enumeration.Mode;
import de.kiaim.cinnamon.platform.model.enumeration.RowSelector;
import de.kiaim.cinnamon.platform.model.file.FileConfiguration;
import de.kiaim.cinnamon.platform.repository.DataSetRepository;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.test.util.*;
import de.kiaim.cinnamon.test.platform.ControllerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.oneOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
	void getFile() throws Exception {
		postFile(false);

		mockMvc.perform(get("/api/data/file"))
		       .andExpect(status().isOk())
		       .andExpect(content().json("{name: 'file.csv', type: 'CSV', numberOfAttributes: 6}"));
	}

	@Test
	void getFileNoFile() throws Exception {
		mockMvc.perform(get("/api/data/file"))
		       .andExpect(status().isOk())
		       .andExpect(content().json("{name: null, type: null, numberOfAttributes: null}"));
	}

	@Test
	void postFile() throws Exception {
		postFile(false);
	}

	@Test
	void postFileMissingFile() throws Exception {
		FileConfiguration fileConfiguration = FileConfigurationTestHelper.generateFileConfiguration();

		mockMvc.perform(multipart("/api/data/file")
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

		mockMvc.perform(multipart("/api/data/file")
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

		mockMvc.perform(multipart("/api/data/file")
				                .file(file)
				                .param("fileConfiguration", objectMapper.writeValueAsString(fileConfiguration)))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("Missing file extension"));
	}


	@Test
	void postFileMissingFileConfiguration() throws Exception {
		MockMultipartFile file = ResourceHelper.loadCsvFile();

		mockMvc.perform(multipart("/api/data/file")
				                .file(file))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("Request validation failed"))
		       .andExpect(validationError("fileConfiguration", "File Configuration must be present!"));
	}

	@Test
	void estimateConfiguration() throws Exception {
		postFile(false);

		final String result = mockMvc.perform(get("/api/data/estimation"))
		                             .andExpect(status().isOk())
		                             .andReturn().getResponse().getContentAsString();

		final DataConfigurationEstimation estimation = objectMapper.readValue(result, DataConfigurationEstimation.class);

		final DataConfiguration expectedConfiguration = DataConfigurationTestHelper.generateEstimatedConfiguration();
		assertEquals(expectedConfiguration, estimation.getDataConfiguration(), "Returned configuration is wrong!");

		float[] expectedConfidences = {1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f};
		assertArrayEquals(expectedConfidences, estimation.getConfidences());
	}

	@Test
	void estimateConfigurationNoFile() throws Exception {
		mockMvc.perform(get("/api/data/estimation"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("Estimating the data configuration requires the file for the dataset to be selected!"));
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
	void storeConfigUndefinedDataType() throws Exception {
		var configuration = DataConfigurationTestHelper.generateDataConfiguration();
		configuration.getConfigurations().get(0).setType(DataType.UNDEFINED);
		var string = jsonMapper.writeValueAsString(configuration);

		mockMvc.perform(multipart("/api/data/configuration")
				                .param("configuration", string))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("Request validation failed"))
		       .andExpect(errorCode(ApiException.assembleErrorCode("3", "2", "1")));
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	void storeDataAndDeleteData() throws Exception {
		postFile();

		final DataConfiguration configuration = DataConfigurationTestHelper.generateDataConfiguration();

		String result = mockMvc.perform(multipart("/api/data")
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
		assertFalse(dataSetEntity.isConfirmedData(), "Flag that the data is confirmed should be false!");

		mockMvc.perform(MockMvcRequestBuilders.delete("/api/data")
		                                      .contentType(MediaType.APPLICATION_JSON_VALUE))
		       .andExpect(status().isOk());

		assertFalse(existsTable(dataSetId), "Table should be deleted!");

		final DataSetEntity deletedDataSet = dataSetRepository.findById(dataSetId).orElse(null);
		assertFalse(deletedDataSet.isStoredData(), "Flag that the data is stored should be false!");
	}

	@Test
	void storeDataAndUpdateConfig() throws Exception {
		postFile();

		final DataConfiguration configuration = DataConfigurationTestHelper.generateDataConfiguration();
		var result = mockMvc.perform(multipart("/api/data")
				                             .param("configuration",
				                                    objectMapper.writeValueAsString(configuration)))
		                    .andExpect(status().isOk())
		                    .andReturn().getResponse().getContentAsString();
		final long dataSetId = assertDoesNotThrow(() -> Long.parseLong(result.trim()));
		final DataSetEntity dataset = dataSetRepository.findById(dataSetId).get();

		final DataConfiguration configurationUpdate = DataConfigurationTestHelper.generateDataConfiguration("[0-9]*");
		mockMvc.perform(multipart("/api/data/configuration")
				                .param("configuration",
				                       objectMapper.writeValueAsString(
						                       configurationUpdate)))
		       .andExpect(status().isOk());

		assertFalse(dataset.isStoredData(), "Dataset should have been deleted!");
	}

	@Test
	void storeDataNoFile() throws Exception {
		final DataConfiguration configuration = DataConfigurationTestHelper.generateDataConfiguration();
		mockMvc.perform(multipart("/api/data")
				                .param("configuration",
				                       objectMapper.writeValueAsString(configuration)))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("Storing the dataset requires the file for the dataset to be selected!"));
	}

	@Test
	void confirmDataAndDeleteData() throws Exception {
		final Long dataSetId = postData(false);

		mockMvc.perform(post("/api/data/confirm"))
		       .andExpect(status().isOk());

		final DataSetEntity dataSetEntity = dataSetRepository.findById(dataSetId).get();
		assertTrue(dataSetEntity.isConfirmedData(), "Flag that the data is confirmed should be true!");

		mockMvc.perform(MockMvcRequestBuilders.delete("/api/data")
		                                      .contentType(MediaType.APPLICATION_JSON_VALUE))
		       .andExpect(status().isOk());

		final DataSetEntity deletedDataSet = dataSetRepository.findById(dataSetId).get();
		assertFalse(deletedDataSet.isConfirmedData(), "Flag that the data is confirmed should be false!");
	}

	@Test
	void confirmDataAndUpdateConfig() throws Exception {
		postFile();

		final DataConfiguration configuration = DataConfigurationTestHelper.generateDataConfiguration();
		var result = mockMvc.perform(multipart("/api/data")
				                             .param("configuration",
				                                    objectMapper.writeValueAsString(configuration)))
		                    .andExpect(status().isOk())
		                    .andReturn().getResponse().getContentAsString();
		final long dataSetId = assertDoesNotThrow(() -> Long.parseLong(result.trim()));
		final DataSetEntity dataset = dataSetRepository.findById(dataSetId).get();

		mockMvc.perform(post("/api/data/confirm"))
		       .andExpect(status().isOk());

		assertTrue(dataset.isConfirmedData(), "Dataset should have been confirmed!");

		final DataConfiguration configurationUpdate = DataConfigurationTestHelper.generateDataConfiguration("[0-9]*");
		mockMvc.perform(multipart("/api/data/configuration")
				                .param("configuration",
				                       objectMapper.writeValueAsString(
						                       configurationUpdate)))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("The data has already been stored!"));
	}

	@Test
	void loadConfigYaml() throws Exception {
		postData();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/configuration")
		                                      .accept(CustomMediaType.APPLICATION_YAML)
		                                      .param("selector", "original"))
		       .andExpect(status().isOk())
		       .andExpect(
				       content().string(DataConfigurationTestHelper.generateDataConfigurationAsYaml()));
	}

	@Test
	void loadConfigJson() throws Exception {
		postData();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/configuration")
		                                      .accept(MediaType.APPLICATION_JSON)
		                                      .param("selector", "original"))
		       .andExpect(status().isOk())
		       .andExpect(content().string(DataConfigurationTestHelper.generateDataConfigurationAsJson()));
	}

	@Test
	void getDataInfo() throws Exception {
		postData();

		mockMvc.perform(get("/api/data/info")
				                .param("selector", "original"))
		       .andExpect(status().isOk())
		       .andExpect(content().json("{numberRows: 3, numberInvalidRows:  1, hasHoldOutSplit: false, numberHoldOutRows: 0, numberInvalidHoldOutRows: 0}"));
	}

	@Test
	void getDataInfoNoData() throws Exception {
		mockMvc.perform(get("/api/data/info")
				                .param("selector", "original"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("The project '" + testProject.getId() + "' does not contain an original data set!"));
	}

	// ================================================================================================================
	// region loadData()
	// ================================================================================================================

	@Test
	void loadData() throws Exception {
		postData();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/data")
		                                      .accept(CustomMediaType.APPLICATION_YAML)
		                                      .param("selector", "original"))
		       .andExpect(status().isOk())
		       .andExpect(content().string(DataSetTestHelper.generateDataAsYaml()));
	}

	@Test
	void loadDataNoDataSet() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/data")
		                                      .param("selector", "original"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage(
				       "The project '" + testProject.getId() + "' does not contain an original data set!"));
	}

	@WithAnonymousUser
	@Test
	void loadDataNoPermissions() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/data")
		                                      .param("selector", "original"))
		       .andExpect(status().isUnauthorized());
	}

	@Test
	void loadDataColumns() throws Exception {
		postData();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/data")
		                                      .accept(CustomMediaType.APPLICATION_YAML)
		                                      .param("selector", "original")
		                                      .param("columns", "column4_integer,column0_boolean"))
		       .andExpect(status().isOk())
		       .andExpect(content().string(DataSetTestHelper.generateDataColumnsAsYaml()));
	}

	@Test
	void loadDataInvalidColumns() throws Exception {
		postData();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/data")
		                                      .param("selector", "original")
		                                      .param("columns", "invalid1,column4_integer,invalid2"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("Data set does not contain columns with names: 'invalid1', 'invalid2'"));
	}

	@Test
	void loadDataEncoding() throws Exception {
		postData(true);

		final String defaultNullEncoding = "N/A";
		final String formatErrorEncoding = ":(";

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/data")
		                                      .accept(CustomMediaType.APPLICATION_YAML)
		                                      .param("selector", "original")
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

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/data")
		                                      .accept(CustomMediaType.APPLICATION_YAML)
		                                      .param("selector", "original")
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

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data/data")
		                                      .accept(CustomMediaType.APPLICATION_YAML)
		                                      .param("selector", "original")
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
	// region generateHoldOutSplit()
	// ================================================================================================================

	@Test
	void generateHoldOutSplit() throws Exception {
		final float holdOutPercentage = 0.5f;

		postData(false);

		mockMvc.perform(post("/api/data/hold-out")
				                .param("holdOutPercentage", String.valueOf(holdOutPercentage)))
		       .andExpect(status().isOk());

		final ProjectEntity project = getTestProject();
		assertTrue(project.getOriginalData().isHasHoldOut(), "Hold-out split should have been generated!");
		assertEquals(holdOutPercentage, project.getOriginalData().getHoldOutPercentage(),
		             "Hold-out percentage not set correctly!");

		mockMvc.perform(get("/api/data/data")
				                .param("selector", "original")
				                .param("holdOutSelector", HoldOutSelector.HOLD_OUT.name())
				                .accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().json("[[false,'2023-11-20','2023-11-20T12:50:27.123456',2.4,24,'Bye World!']]"));

		mockMvc.perform(get("/api/data/data")
				                .param("selector", "original")
				                .param("holdOutSelector", HoldOutSelector.NOT_HOLD_OUT.name())
				                .accept(MediaType.APPLICATION_JSON))
		       .andExpect(status().isOk())
		       .andExpect(content().json("[[true,'2023-11-20','2023-11-20T12:50:27.123456',4.2,42,'Hello World!']]"));

		mockMvc.perform(get("/api/data/data")
				                .param("selector", "original")
				                .param("holdOutSelector", HoldOutSelector.ALL.name())
				                .accept(MediaType.APPLICATION_JSON))
		       .andExpect(status().isOk())
		       .andExpect(content().json("[[false,'2023-11-20','2023-11-20T12:50:27.123456',2.4,24,'Bye World!'],[true,'2023-11-20','2023-11-20T12:50:27.123456',4.2,42,'Hello World!']]"));
	}

	@Test
	void generateHoldOutSplitInvalidPercentageBig() throws Exception {
		postData(false);

		mockMvc.perform(post("/api/data/hold-out")
				                .param("holdOutPercentage", String.valueOf(1.3)))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_3_2_1"))
		       .andExpect(errorMessage("Request validation failed"))
		       .andExpect(validationError("holdOutPercentage", "Value must be between 0.0 and 1.0"));
	}

	@Test
	void generateHoldOutSplitInvalidPercentageNegative() throws Exception {
		postData(false);

		mockMvc.perform(post("/api/data/hold-out")
				                .param("holdOutPercentage", String.valueOf(-0.01)))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_3_2_1"))
		       .andExpect(errorMessage("Request validation failed"))
		       .andExpect(validationError("holdOutPercentage", "Value must be between 0.0 and 1.0"));
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

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data")
		                                      .accept(CustomMediaType.APPLICATION_YAML)
		                                      .param("selector", "original"))
		       .andExpect(status().isOk())
		       .andExpect(content().string(DataSetTestHelper.generateDataSetAsYaml()));
	}

	@Test
	void loadDataSetJson() throws Exception {
		postData();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data")
		                                      .accept(MediaType.APPLICATION_JSON)
		                                      .param("selector", "original"))
		       .andExpect(status().isOk())
		       .andExpect(content().string(DataSetTestHelper.generateDataSetAsJson()));
	}

	@Test
	void loadDataSetNoDataSet() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/api/data")
		                                      .param("selector", "original"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage(
				       "The project '" + testProject.getId() + "' does not contain an original data set!"));
	}

	@WithAnonymousUser
	@Test
	void loadDataSetNoPermissions() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/api/data")
		                                      .param("selector", "original"))
		       .andExpect(status().isUnauthorized());
	}

	@Test
	void loadDataSetColumns() throws Exception {
		postData();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data")
		                                      .accept(CustomMediaType.APPLICATION_YAML)
		                                      .param("selector", "original")
		                                      .param("columns", "column4_integer,column0_boolean"))
		       .andExpect(status().isOk())
		       .andExpect(content().string(DataSetTestHelper.generateDataSetColumnsAsYaml()));
	}

	@Test
	void loadDataSetInvalidColumns() throws Exception {
		postData();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data")
		                                      .param("selector", "original")
		                                      .param("columns", "invalid1,column4_integer,invalid2"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("Data set does not contain columns with names: 'invalid1', 'invalid2'"));
	}

	@Test
	void loadDataSetEncoding() throws Exception {
		postData(true);

		final String defaultNullEncoding = "N/A";
		final String formatErrorEncoding = ":(";

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data")
		                                      .accept(CustomMediaType.APPLICATION_YAML)
		                                      .param("selector", "original")
		                                      .param("defaultNullEncoding", defaultNullEncoding)
		                                      .param("formatErrorEncoding", formatErrorEncoding))
		       .andExpect(status().isOk())
		       .andExpect(
				       content().string(DataSetTestHelper.generateDataSetAsYaml(wrapInQuotes(defaultNullEncoding),
				                                                                wrapInQuotes(formatErrorEncoding))));
	}

	@Test
	void loadDataSetEncodingJson() throws Exception {
		postData(true);

		final String defaultNullEncoding = "N/A";
		final String formatErrorEncoding = ":(";

		mockMvc.perform(MockMvcRequestBuilders.get("/api/data")
		                                      .accept(MediaType.APPLICATION_JSON)
		                                      .param("selector", "original")
		                                      .param("defaultNullEncoding", defaultNullEncoding)
		                                      .param("formatErrorEncoding", formatErrorEncoding))
		       .andExpect(status().isOk())
		       .andExpect(content().string(DataSetTestHelper.generateDataSetAsJson(true,
		                                                                           wrapInQuotes(defaultNullEncoding),
		                                                                           wrapInQuotes(formatErrorEncoding))));
	}

	// ================================================================================================================
	// endregion loadDataSet()
	// ================================================================================================================

	@Test
	void loadTransformationResult() throws Exception {
		postData(true);

		mockMvc.perform(get("/api/data/transformationResult")
				                .param("selector", "original"))
		       .andExpect(status().isOk())
		       .andExpect(content().string(oneOf(TransformationResultTestHelper.generateTransformationResultAsJsonA(),
		                                         TransformationResultTestHelper.generateTransformationResultAsJsonB())));
	}

	@Test
	void loadTransformationResultPage() throws Exception {
		postData();

		mockMvc.perform(get("/api/data/transformationResult/page")
				                .param("selector", "original")
				                .param("page", "2")
				                .param("perPage", "1"))
		       .andExpect(status().isOk())
		       .andExpect(content().json(
				       "{'data':[[false,'2023-11-20','2023-11-20T12:50:27.123456',2.4,24,'Bye World!']],'transformationErrors':[],'rowNumbers':null,'page':2,'perPage':1,total:3,'totalPages':3}"));
	}

	@Test
	void loadTransformationResultPageWithErrors() throws Exception {
		postData();

		mockMvc.perform(get("/api/data/transformationResult/page")
				                .param("selector", "original")
				                .param("page", "2")
				                .param("perPage", "2"))
		       .andExpect(status().isOk())
		       .andExpect(content().json(
				       "{'data':[[true,'2023-11-20',null,4.2,null,'Hello World!']],'transformationErrors':[{'index':0,'dataTransformationErrors':[{'index':2,'errorType':'MISSING_VALUE',rawValue:''},{'index':4,'errorType':'FORMAT_ERROR',rawValue:'forty two'}]}],'rowNumbers':null,'page':2,'perPage':2,total:3,'totalPages':2}"));
	}

	@Test
	void loadTransformationResultPageEncodedErrors() throws Exception {
		postData();

		mockMvc.perform(get("/api/data/transformationResult/page")
				                .param("selector", "original")
				                .param("page", "3")
				                .param("perPage", "1")
				                .param("formatErrorEncoding", "$value"))
		       .andExpect(status().isOk())
		       .andExpect(content().json(
				       "{'data':[[true,'2023-11-20',null,4.2,'forty two','Hello World!']],'transformationErrors':[{'index':0,'dataTransformationErrors':[{'index':2,'errorType':'MISSING_VALUE',rawValue:''},{'index':4,'errorType':'FORMAT_ERROR',rawValue:'forty two'}]}],'rowNumbers':null,'page':3,'perPage':1,total:3,'totalPages':3}"));
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	void loadTransformationResultPageSelectErrors() throws Exception {
		postData();

		mockMvc.perform(get("/api/data/transformationResult/page")
				                .param("selector", "original")
				                .param("page", "1")
				                .param("perPage", "2")
				                .param("rowSelector", RowSelector.ERRORS.name())
				                .param("formatErrorEncoding", "$value"))
		       .andExpect(status().isOk())
		       .andExpect(content().json(
				       "{'data':[[true,'2023-11-20',null,4.2,'forty two','Hello World!']],'transformationErrors':[{'index':0,'dataTransformationErrors':[{'index':2,'errorType':'MISSING_VALUE',rawValue:''},{'index':4,'errorType':'FORMAT_ERROR',rawValue:'forty two'}]}],'rowNumbers':[2],'page':1,'perPage':2,total:1,'totalPages':1}"));
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	void loadTransformationResultPageSelectValid() throws Exception {
		postData();

		mockMvc.perform(get("/api/data/transformationResult/page")
				                .param("selector", "original")
				                .param("page", "1")
				                .param("perPage", "1")
				                .param("rowSelector", RowSelector.VALID.name())
				                .param("formatErrorEncoding", "$value"))
		       .andExpect(status().isOk())
		       .andExpect(content().json(
				       "{'data':[[true,'2023-11-20','2023-11-20T12:50:27.123456',4.2,42,'Hello World!']],'transformationErrors':[],'rowNumbers':[0],'page':1,'perPage':1,total:2,'totalPages':2}"));
	}

	@Test
	void loadTransformationResultPageSelectColumn() throws Exception {
		postData();

		mockMvc.perform(get("/api/data/transformationResult/page")
				                .param("selector", "original")
				                .param("page", "1")
				                .param("perPage", "10")
				                .param("columns", "column4_integer")
				                .param("rowSelector", RowSelector.ALL.name())
				                .param("formatErrorEncoding", "$value"))
		       .andExpect(status().isOk())
		       .andExpect(content().json(
				       "{'data':[[42],[24],['forty two']],'transformationErrors':[{'index':2,'dataTransformationErrors':[{'index':0,'errorType':'FORMAT_ERROR',rawValue:'forty two'}]}],'rowNumbers':null,'page':1,'perPage':10,total:3,'totalPages':1}"));
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
		postFile();

		mockMvc.perform(multipart("/api/data/configuration")
				                .param("configuration", configuration))
		       .andExpect(status().isOk());
		final DataSetEntity dataSetEntity = getTestProject().getOriginalData().getDataSet();
		final var dataSetId = dataSetEntity.getId();

		UserEntity testUser = getTestUser();
		assertFalse(existsTable(dataSetId), "Table should not exist!");
		assertTrue(existsDataSet(dataSetId), "Configuration has not been persisted!");
		assertNotNull(testUser.getProject(), "User has not been associated with the dataset!");
		assertEquals(".*",
		             ((StringPatternConfiguration) testUser.getProject().getOriginalData().getDataSet()
		                                                   .getDataConfiguration().getConfigurations().get(5)
		                                                   .getConfigurations().get(0))
				             .getPattern(),
		             "Type of first column does not match!");

		final DataConfiguration configurationUpdate = DataConfigurationTestHelper.generateDataConfiguration("[0-9]*");

		mockMvc.perform(multipart("/api/data/configuration")
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
		             ((StringPatternConfiguration) testUser.getProject().getOriginalData().getDataSet()
		                                                   .getDataConfiguration().getConfigurations().get(5)
		                                                   .getConfigurations().get(0)).getPattern(),
		             "Type of first column does not match!");
	}

}
