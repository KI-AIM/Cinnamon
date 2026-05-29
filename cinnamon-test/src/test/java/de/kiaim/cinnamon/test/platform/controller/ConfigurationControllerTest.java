package de.kiaim.cinnamon.test.platform.controller;

import de.kiaim.cinnamon.model.configuration.ConfigurationFile;
import de.kiaim.cinnamon.model.configuration.data.file.FileType;
import de.kiaim.cinnamon.model.configuration.project.MetricImportance;
import de.kiaim.cinnamon.model.dto.ConfigurationImportParameters;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.entity.CsvFileConfigurationEntity;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.test.platform.ControllerTest;
import de.kiaim.cinnamon.test.util.*;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
@WithMockWebServer
@WithUserDetails("test_user")
class ConfigurationControllerTest extends ControllerTest {

	@Autowired private CinnamonConfiguration cinnamonConfiguration;

	@Autowired
	ProjectService projectService;

	private MockWebServer mockWebServer;

	@Test
	void info() throws Exception {
		mockMvc.perform(get("/api/config/info")
				                .param("name", CONFIGURATION_NAME))
		       .andExpect(status().isOk())
		       .andExpect(
				       content().json("{processes: [{job: 'anonymization', skip: false, holdOutFulfilled: true, configured: false}]}"));
	}

	@Test
	void infoSkippedWithConfiguration() throws Exception {
		mockMvc.perform(post("/api/config")
				                .param("configurationName", CONFIGURATION_NAME)
				                .param("url", "/start_synthetization_process/ctgan")
				                .param("configuration", AlgorithmTestHelper.generateAlgorithmConfigurationYaml())
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isOk());
		mockMvc.perform(post("/api/process/configure")
				                .param("jobName", CONFIGURATION_NAME)
				                .param("skip", "true")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isOk());

		mockMvc.perform(get("/api/config/info")
				                .param("name", CONFIGURATION_NAME))
		       .andExpect(status().isOk())
		       .andExpect(
				       content().json("{processes: [{job: 'anonymization', skip: true, holdOutFulfilled: true, configured: true}]}"));
	}

	@Test
	void infoInvalidName() throws Exception {
		mockMvc.perform(get("/api/config/info")
				                .param("name", "invalid"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_1_2_1"))
		       .andExpect(errorMessage("No configuration with name 'invalid' registered!"));
	}

	@Test
	void store() throws Exception {
		final String config = AlgorithmTestHelper.generateAlgorithmConfigurationYaml();

		mockMvc.perform(post("/api/config")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE)
				                .param("configuration", config))
		       .andExpect(status().isOk());

		final UserEntity user = getTestUser();
		final ProjectEntity project = user.getProject();
		assertNotNull(project, "The configuration has not been created!");
		testConfiguration(project, config);
	}

	@Test
	public void importConfigurationsNoYAML() throws Exception {
		final String configuration = "invalid";
		var file = new MockMultipartFile("configuration", "file.yaml", "application/yaml", configuration.getBytes());

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/config/import")
		                                      .file(file))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_1_14_2"));
	}

	@Test
	public void importConfigurationsNoAlgorithmSelector() throws Exception {
		final String configuration = """
		                             anonymization:
		                                param1: 42
		                             """;
		var file = new MockMultipartFile("configuration", "file.yaml", "text/yaml", configuration.getBytes());

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/config/import").file(file))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                 	parameters: {
		                                 		allowPartialImport: true,
		                                 		configurationsToImport: null
		                                 	},
		                                 	status: 'PARTIAL_ERROR',
		                                 	configurationImportSummaries:  [
		                                 		{configurationName: 'anonymization', status: 'ERROR', errorCode: 'PLATFORM_1_15_2'}
		                                 	]
		                                 }
		                                 """));
	}

	@Test
	public void importConfigurations() throws Exception {
		final String configuration = """
		                             anonymization:
		                                algorithm:
		                                    id: 'tabular'
		                                    version: '1.0.0'
		                                param1: 42
		                             """;
		var file = new MockMultipartFile("configuration", "file.yaml", "text/yaml", configuration.getBytes());

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/config/import")
		                                      .file(file))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                 	parameters: {
		                                 		allowPartialImport: true,
		                                 		configurationsToImport: null
		                                 	},
		                                 	status: 'SUCCESS',
		                                 	configurationImportSummaries:  [
		                                 		{configurationName: 'anonymization', status: 'SUCCESS', errorCode: null}
		                                 	]
		                                 }
		                                 """));

		var expected = """
		               anonymization:
		                 algorithm:
		                   id: "tabular"
		                   version: "1.0.0"
		                 param1: 42
		               """;
		testImportedConfiguration("anonymization", expected);
	}

	@Test
	public void importConfigurationsJSON() throws Exception {
		final String configuration = """
		                             {
		                                "anonymization": {
		                                    "algorithm": {
		                                        "id": "tabular",
		                                        "version": "1.0.0"
		                                    },
		                                    "param1": 42
		                                }
		                             }
		                             """;
		var file = new MockMultipartFile("configuration", "file.json", "text/json", configuration.getBytes());

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/config/import").file(file))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                 	parameters: {
		                                 		allowPartialImport: true,
		                                 		configurationsToImport: null
		                                 	},
		                                 	status: 'SUCCESS',
		                                 	configurationImportSummaries:  [
		                                 		{configurationName: 'anonymization', status: 'SUCCESS', errorCode: null}
		                                 	]
		                                 }
		                                 """));

		var expected = """
		               anonymization:
		                 algorithm:
		                   id: "tabular"
		                   version: "1.0.0"
		                 param1: 42
		               """;
		testImportedConfiguration("anonymization", expected);
	}

	@Test
	public void importConfigurationsDataConfiguration() throws Exception {
		postFile(false, false);

		final var configuration = DataConfigurationTestHelper.generateDataConfigurationAsYaml();

		var file = new MockMultipartFile("configuration", "file.json", "text/json", configuration.getBytes());

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/config/import").file(file))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                 	parameters: {
		                                 		allowPartialImport: true,
		                                 		configurationsToImport: null
		                                 	},
		                                 	status: 'SUCCESS',
		                                 	configurationImportSummaries:  [
		                                 		{configurationName: 'configurations', status: 'SUCCESS', errorCode: null}
		                                 	]
		                                 }
		                                 """));

		var dataset = getTestProject().getOriginalData().getDataSet();
		assertNotNull(dataset);
		assertEquals(DataConfigurationTestHelper.generateDataConfiguration(), dataset.getDataConfiguration());
	}

	@Test
	public void importConfigurationsProjectConfiguration() throws Exception {
		final var configuration = ProjectConfigurationTestHelper.generateProjectConfigurationAsYaml();
		var file = new MockMultipartFile("configuration", "file.json", "text/json", configuration.getBytes());

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/config/import").file(file))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                 	parameters: {
		                                 		allowPartialImport: true,
		                                 		configurationsToImport: null
		                                 	},
		                                 	status: 'SUCCESS',
		                                 	configurationImportSummaries:  [
		                                 		{configurationName: 'project', status: 'SUCCESS', errorCode: null}
		                                 	]
		                                 }
		                                 """));

		var projectConfig = getTestProject().getProjectConfiguration();
		assertEquals("testProject", projectConfig.getProjectName());
		assertEquals("contact@example.com", projectConfig.getContactMail());
		assertNull(projectConfig.getContactUrl());
		assertNotNull(projectConfig.getMetricConfiguration());
		assertEquals("Fluffy Unicorn", projectConfig.getMetricConfiguration().getColorScheme());
		assertTrue(projectConfig.getMetricConfiguration().getUseUserDefinedImportance());
		assertEquals(MetricImportance.IMPORTANT,
		             projectConfig.getMetricConfiguration().getUserDefinedImportance().get("MetricA"));
		assertEquals(MetricImportance.ADDITIONAL,
		             projectConfig.getMetricConfiguration().getUserDefinedImportance().get("MetricB"));
		assertEquals(MetricImportance.NOT_RELEVANT,
		             projectConfig.getMetricConfiguration().getUserDefinedImportance().get("MetricC"));
	}

	@Test
	public void importConfigurationsProjectConfigurationInvalid() throws Exception {
		var configuration = ProjectConfigurationTestHelper.generateProjectConfigurationAsYaml();
		configuration = configuration.replace("\"testProject\"", "null");
		var file = new MockMultipartFile("configuration", "file.json", "text/json", configuration.getBytes());

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/config/import").file(file))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                 	parameters: {
		                                 		allowPartialImport: true,
		                                 		configurationsToImport: null
		                                 	},
		                                 	status: 'PARTIAL_ERROR',
		                                 	configurationImportSummaries: [{
		                                 		configurationName: 'project',
		                                 		status: 'ERROR',
		                                 		errorCode: "PLATFORM_3_2_1",
		                                 		validationErrors: {
		                                 			projectName: ["must not be blank"]
		                                 		}
		                                 	}]
		                                 }
		                                 """));
	}

	@Test
	public void importConfigurationsDataSourceConfiguration() throws Exception {
		String config = """
		                dataSource:
		                  fileType: "CSV"
		                  csvFileConfiguration:
		                    hasHeader: false
		                """;

		var file = new MockMultipartFile("configuration", "file.yaml", "text/yaml", config.getBytes());

		mockMvc.perform(multipart("/api/config/import").file(file))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                 	parameters: {
		                                 		allowPartialImport: true,
		                                 		configurationsToImport: null
		                                 	},
		                                 	status: 'SUCCESS',
		                                 	configurationImportSummaries:  [
		                                 		{configurationName: 'dataSource', status: 'SUCCESS', errorCode: null}
		                                 	]
		                                 }
		                                 """));

		var project = getTestProject();
		var dataSourceConfig = project.getOriginalData().getFile().getFileConfiguration();
		assertNotNull(dataSourceConfig);
		assertEquals(FileType.CSV, dataSourceConfig.getFileType());
		assertInstanceOf(CsvFileConfigurationEntity.class, dataSourceConfig);

		var csvFileConfiguration = (CsvFileConfigurationEntity) dataSourceConfig;
		assertEquals(false, csvFileConfiguration.getHasHeader());
	}

	@Test
	public void importConfigurationsDataSourceConfigurationInvalid() throws Exception {
		String config = """
		                dataSource:
		                  fileType: "CSV"
		                """;

		var file = new MockMultipartFile("configuration", "file.yaml", "text/yaml", config.getBytes());

		mockMvc.perform(multipart("/api/config/import").file(file))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                 	parameters: {
		                                 		allowPartialImport: true,
		                                 		configurationsToImport: null
		                                 	},
		                                 	status: 'PARTIAL_ERROR',
		                                 	configurationImportSummaries:  [{
		                                 		configurationName: 'dataSource',
		                                 		status: 'ERROR',
		                                 		errorCode: 'PLATFORM_3_2_1',
		                                 		validationErrors: {csvFileConfiguration: ["CSV file configuration must be set for CSV files!"]}
		                                 	}]
		                                 }
		                                 """));
	}

	@Test
	public void importConfigurationsDatasetConfiguration() throws Exception {
		String config = """
		                dataset:
		                  createHoldOutSplit: true
		                  holdOutSplitPercentage: 0.2
		                """;

		var file = new MockMultipartFile("configuration", "file.yaml", "text/yaml", config.getBytes());
		mockMvc.perform(multipart("/api/config/import").file(file))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                 	parameters: {
		                                 		allowPartialImport: true,
		                                 		configurationsToImport: null
		                                 	},
		                                 	status: 'SUCCESS',
		                                 	configurationImportSummaries:  [
		                                 		{configurationName: 'dataset', status: 'SUCCESS', errorCode: null}
		                                 	]
		                                 }
		                                 """));

		var project = getTestProject();
		assertTrue(project.getOriginalData().getDatasetConfiguration().isCreateHoldOutSplit());
		assertEquals(0.2, project.getOriginalData().getDatasetConfiguration().getHoldOutSplitPercentage(), 0.0001);
	}

	@Test
	public void importConfigurationsPipelinesConfiguration() throws Exception {
		// Configuration must be available before importing the pipeline
		mockMvc.perform(post("/api/config")
				                .param("configuration", AlgorithmTestHelper.generateAlgorithmConfigurationYaml())
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
		       .andExpect(status().isOk());

		final String configuration = """
		                             pipeline:
		                               pipelines:
		                               - jobs:
		                                 - name: anonymization
		                                 - name: synthetization
		                                   enabled: false
		                             """;
		var file = new MockMultipartFile("configuration", "file.yaml", "text/yaml", configuration.getBytes());

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/config/import")
		                                      .file(file))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                 	parameters: {
		                                 		allowPartialImport: true,
		                                 		configurationsToImport: null
		                                 	},
		                                 	status: 'SUCCESS',
		                                 	configurationImportSummaries: [
		                                 		{configurationName: 'pipeline', status: 'SUCCESS', errorCode: null}
		                                 	]
		                                 }
		                                 """));

		var project = getTestProject();
		var pipeline = project.getPipelines().get(0);
		var firstStage = pipeline.getStages().get(0);
		assertFalse(firstStage.getProcesses().get(0).isSkip());
		assertNotNull(firstStage.getProcesses().get(0).getConfiguration());
		assertTrue(firstStage.getProcesses().get(1).isSkip());
		var secondStage = pipeline.getStages().get(1);
		assertTrue(secondStage.getProcesses().get(0).isSkip());
		assertTrue(secondStage.getProcesses().get(1).isSkip());
	}

	@Test
	public void importConfigurationsInvalid() throws Exception {
		final String configuration = """
		                             invalid_name:
		                                param2: 42
		                             """;
		var file = new MockMultipartFile("configuration", "file.yaml", "text/yaml", configuration.getBytes());

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/config/import")
		                                      .file(file))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                 	parameters: {
		                                 		allowPartialImport: true,
		                                 		configurationsToImport: null
		                                 	},
		                                 	status: 'PARTIAL_ERROR',
		                                 	configurationImportSummaries: [
		                                 		{configurationName: 'invalid_name', status:  'ERROR', errorCode: 'PLATFORM_1_2_1'}
		                                 	]
		                                 }
		                                 """));

		testImportedConfiguration("invalid_name", null);
	}

	@Test
	public void importConfigurationsInvalidNonPartial() throws Exception {
		final String configuration = """
		                             invalid_name:
		                                param2: 42
		                             """;
		var file = new MockMultipartFile("configuration", "file.yaml", "text/yaml", configuration.getBytes());

		ConfigurationImportParameters parameters = new ConfigurationImportParameters();
		parameters.setAllowPartialImport(false);

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/config/import")
		                                      .file(file)
		                                      .param("importParameters", jsonMapper.writeValueAsString(parameters)))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_1_14_3"))
		       .andExpect(content().json("""
		                                 {
		                                 	errorDetails: {
		                                 		configurationImportSummary: {
		                                 			parameters: {
		                                 				allowPartialImport: false,
		                                 				configurationsToImport: null
		                                 			},
		                                 			status: 'ERROR',
		                                 			configurationImportSummaries: [
		                                 				{configurationName: 'invalid_name', status:  'ERROR', errorCode: 'PLATFORM_1_2_1'}
		                                 			]
		                                 		}
		                                 	}
		                                 }
		                                 """));

		testImportedConfiguration("invalid_name", null);
	}

	@Test
	public void importConfigurationsSomeInvalid() throws Exception {
		final String configuration = """
		                             anonymization:
		                                algorithm:
		                                    id: 'tabular'
		                                    version: '1.0.0'
		                                param1: 42
		                             invalid_name:
		                                param2: 42
		                             """;
		var file = new MockMultipartFile("configuration", "file.yaml", "text/yaml", configuration.getBytes());

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/config/import").file(file))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                 	parameters: {
		                                 		allowPartialImport: true,
		                                 		configurationsToImport: null
		                                 	},
		                                 	status: 'PARTIAL_ERROR',
		                                 	configurationImportSummaries: [
		                                 		{configurationName: 'anonymization', status: 'SUCCESS', errorCode: null},
		                                 		{configurationName: 'invalid_name', status:  'ERROR', errorCode: 'PLATFORM_1_2_1'}
		                                 	]
		                                 }
		                                 """));

		var expected = """
		               anonymization:
		                 algorithm:
		                   id: "tabular"
		                   version: "1.0.0"
		                 param1: 42
		               """;
		testImportedConfiguration("anonymization", expected);
		testImportedConfiguration("invalid_name", null);
	}

	@Test
	public void importConfigurationsSelected() throws Exception {
		final String configuration = """
		                             anonymization:
		                                algorithm:
		                                    id: 'tabular'
		                                    version: '1.0.0'
		                                param1: 42
		                             invalid_name:
		                                param2: 42
		                             """;
		var file = new MockMultipartFile("configuration", "file.yaml", "text/yaml", configuration.getBytes());

		ConfigurationImportParameters parameters = new ConfigurationImportParameters();
		parameters.setConfigurationsToImport(Set.of("anonymization"));

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/config/import")
		                                      .file(file)
		                                      .param("importParameters", jsonMapper.writeValueAsString(parameters)))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                 	parameters: {
		                                 		allowPartialImport: true,
		                                 		configurationsToImport: ['anonymization']
		                                 	},
		                                 	status: 'SUCCESS',
		                                 	configurationImportSummaries: [
		                                 		{configurationName: 'anonymization', status: 'SUCCESS', errorCode: null},
		                                 		{configurationName: 'invalid_name', status:  'IGNORED', errorCode: null}
		                                 	]
		                                 }
		                                 """));

		var expected = """
		               anonymization:
		                 algorithm:
		                   id: "tabular"
		                   version: "1.0.0"
		                 param1: 42
		               """;
		testImportedConfiguration("anonymization", expected);
		testImportedConfiguration("invalid_name", null);
	}

	@Test
	public void importConfigurationsOldAnonymization() throws Exception {
		final String configuration = """
		                             anonymization:
		                                privacyModels:
		                                    - name: algorithmA
		                                      type: tabular
		                                attributeConfiguration:
		                                    - attributeProtection: MICRO_AGGREGATION
		                             """;
		var file = new MockMultipartFile("configuration", "file.yaml", "text/yaml", configuration.getBytes());

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/config/import")
		                                      .file(file))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                 	parameters: {
		                                 		allowPartialImport: true,
		                                 		configurationsToImport: null
		                                 	},
		                                 	status: 'SUCCESS',
		                                 	configurationImportSummaries: [
		                                 		{configurationName: 'anonymization', status: 'SUCCESS', errorCode: null}
		                                 	]
		                                 }
		                                 """));

		var expected = AlgorithmTestHelper.generateAlgorithmConfigurationYaml();
		testImportedConfiguration("anonymization", expected);
	}

	@Test
	public void importConfigurationsOldSynthetization() throws Exception {
		final String configuration = """
		                             synthetization_configuration:
		                                 algorithm:
		                                     synthesizer: ctgan
		                                     type: cross-sectional
		                                     version: "0.1"
		                                     model_parameter:
		                                         embedding_dim: 16
		                             """;
		var file = new MockMultipartFile("configuration", "file.yaml", "text/yaml", configuration.getBytes());

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/config/import")
		                                      .file(file))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                 	parameters: {
		                                 		allowPartialImport: true,
		                                 		configurationsToImport: null
		                                 	},
		                                 	status: 'SUCCESS',
		                                 	configurationImportSummaries: [
		                                 		{configurationName: 'synthetization_configuration', status: 'SUCCESS', errorCode: null}
		                                 	]
		                                 }
		                                 """));

		var expected = """
		               synthetization_configuration:
		                 algorithm:
		                   id: "ctgan"
		                   version: "0.1"
		                   synthesizer: "ctgan"
		                   model_parameter:
		                     embedding_dim: 16
		                   type: "cross-sectional"
		               """;
		testImportedConfiguration("synthetization_configuration", expected);
	}

	@Test
	public void importConfigurationsOldEvaluation() throws Exception {
		final String configuration = """
		                             evaluation_configuration:
		                                 data_format: cross-sectional
		                                 resemblance:
		                                     mean: {}
		                                     standard_deviation: {}
		                                 utility:
		                                     machine_learning:
		                                         trains_size: 0.8
		                                         target_variable: "target_variable"
		                             """;
		var file = new MockMultipartFile("configuration", "file.yaml", "text/yaml", configuration.getBytes());

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/config/import")
		                                      .file(file))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                 	parameters: {
		                                 		allowPartialImport: true,
		                                 		configurationsToImport: null
		                                 	},
		                                 	status: 'SUCCESS',
		                                 	configurationImportSummaries: [
		                                 		{configurationName: 'evaluation_configuration', status: 'SUCCESS', errorCode: null}
		                                 	]
		                                 }
		                                 """));

		var expected = """
		               evaluation_configuration:
		                 algorithm:
		                   id: "evaluation"
		                   version: "0.1.0"
		                 data_format: "cross-sectional"
		                 resemblance:
		                   mean: {}
		                   standard_deviation: {}
		                 utility:
		                   machine_learning:
		                     trains_size: 0.8
		                     target_variable: "target_variable"
		               """;
		testImportedConfiguration("evaluation_configuration", expected);
	}

	@Test
	public void importConfigurationsOldRiskAssessment() throws Exception {
		final String configuration = """
		                             risk_assessment_configuration:
		                                 singlingout_uni:
		                                     n_attacks: 10
		                                 attribute_inference:
		                                     n_attacks: 10
		                                 linkage:
		                                     n_attacks: 100
		                                     available_columns:
		                                         - "column0_boolean"
		                                         - "column1_date"
		                             """;
		var file = new MockMultipartFile("configuration", "file.yaml", "text/yaml", configuration.getBytes());

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/config/import")
		                                      .file(file))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                 	parameters: {
		                                 		allowPartialImport: true,
		                                 		configurationsToImport: null
		                                 	},
		                                 	status: 'SUCCESS',
		                                 	configurationImportSummaries: [
		                                 		{configurationName: 'risk_assessment_configuration', status: 'SUCCESS', errorCode: null}
		                                 	]
		                                 }
		                                 """));

		var expected = """
		               risk_assessment_configuration:
		                 algorithm:
		                   id: "risk_assessment"
		                   version: "0.1.0"
		                 attribute_inference:
		                   n_attacks: 10
		                 linkage:
		                   n_attacks: 100
		                   available_columns:
		                   - "column0_boolean"
		                   - "column1_date"
		                 singlingout_uni:
		                   n_attacks: 10
		               """;
		testImportedConfiguration("risk_assessment_configuration", expected);
	}

	@Test
	void load() throws Exception {
		var config = AlgorithmTestHelper.generateAlgorithmConfigurationYaml();

		storeConfiguration(config);

		mockMvc.perform(MockMvcRequestBuilders.get("/api/config")
		                                      .accept(MediaType.APPLICATION_YAML)
		                                      .param("name", CONFIGURATION_NAME))
		       .andExpect(status().isOk())
		       .andExpect(content().contentType(MediaType.APPLICATION_YAML_VALUE))
		       .andExpect(content().string(config));
	}

	@Test
	void loadProjectConfiguration() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/api/config")
		                                      .param("name", ConfigurationFile.PROJECT_CONFIGURATION_KEY))
		       .andExpect(status().isOk())
		       .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
		       .andExpect(content().string(ProjectConfigurationTestHelper.generateProjectConfigurationAsJson()));
	}

	@Test
	void loadDataConfiguration() throws Exception {
		postData();

		mockMvc.perform(MockMvcRequestBuilders.get("/api/config")
		                                      .param("name", ConfigurationFile.DATA_CONFIGURATION_KEY))
		       .andExpect(status().isOk())
		       .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
		       .andExpect(content().string(DataConfigurationTestHelper.generateDataConfigurationAsJson()));
	}

	@Test
	void loadNoConfiguration() throws Exception {
		final String configName = cinnamonConfiguration.getPipeline().getStageList().get(0).getJobList().get(0)
		                                               .getEndpoint().getConfiguration().getConfigurationName();

		final ProjectEntity project = projectService.getProject(getTestUser());

		mockMvc.perform(MockMvcRequestBuilders.get("/api/config")
		                                      .param("name", configName))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage(
				       "No configuration in project '" + project.getId() + "' for name 'anonymization' found!"));
	}

	@Test
	void loadInvalidName() throws Exception {
		final String invalidConfigName = "invalidConfigName";
		final String config = """
				configurations:
				- index: 0
				  name: "column0_boolean"
				  type: "BOOLEAN"
				  scale: "NOMINAL"
				  configurations: []
				""";

		storeConfiguration(config);

		mockMvc.perform(MockMvcRequestBuilders.get("/api/config")
		                                      .param("name", invalidConfigName))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("No configuration with name '" + invalidConfigName + "' registered!"));
	}

	@Test
	void getAvailableAlgorithmsMissingParam() throws Exception {
		mockMvc.perform(get("/api/config/algorithms"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_3_2_1"));
	}

	@Test
	void getAvailableAlgorithmsBlankParam() throws Exception {
		mockMvc.perform(get("/api/config/algorithms")
				                .param("configurationName", " "))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_3_2_1"))
		       .andExpect(validationError("configurationName", "must not be blank"));
	}

	@Test
	void getAlgorithmDefinition() throws Exception {
		postData();
		createHoldOut(0.2f);

		mockWebServer.enqueue(new MockResponse.Builder()
				                      .addHeader("Content-Type", MediaType.APPLICATION_YAML_VALUE)
				                      .code(200)
				                      .body(AlgorithmTestHelper.generateAlgorithmDefinitionYaml())
				                      .build());

		mockMvc.perform(get("/api/config/algorithm")
				                .contentType(MediaType.APPLICATION_JSON_VALUE)
				                .param("configurationName", CONFIGURATION_NAME)
				                .param("definitionPath", "/algorithm"))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                 	modelConfiguration: {
		                                 		parameters: [{},{max_value: 1}]
		                                 	}
		                                 }
		                                 """));
	}

	@Test
	void getAlgorithmDefinitionMissingParam() throws Exception {
		mockMvc.perform(get("/api/config/algorithm"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_3_2_1"));
	}

	private void testImportedConfiguration(final String configurationName, final String content) {
		var project = getTestProject();
		var configList = project.getConfigurations()
		                        .stream()
		                        .filter(c -> c.getConfiguration().getConfigurationName().equals(configurationName))
		                        .findFirst();

		if (content == null) {
			assertTrue(configList.isEmpty());
		} else {
			assertTrue(configList.isPresent());
			assertEquals(1, configList.get().getConfigurations().size());

			var configObject = configList.get().getConfigurations().get(0);
			assertEquals(content, configObject.getConfiguration());
		}
	}
}
