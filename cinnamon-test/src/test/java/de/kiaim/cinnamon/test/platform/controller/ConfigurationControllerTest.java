package de.kiaim.cinnamon.test.platform.controller;

import de.kiaim.cinnamon.model.dto.ConfigurationImportParameters;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.test.platform.ControllerTest;
import de.kiaim.cinnamon.test.util.DataConfigurationTestHelper;
import de.kiaim.cinnamon.test.util.WithMockWebServer;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
				                .param("configuration", "configuration")
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
		final String config = """
				configurations:
				- index: 0
				  name: "column0_boolean"
				  type: "BOOLEAN"
				  scale: "NOMINAL"
				  configurations: []
				""";

		mockMvc.perform(post("/api/config")
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE)
				                .param("configurationName", CONFIGURATION_NAME)
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
	public void importConfigurations() throws Exception {
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
		                                 	status: 'SUCCESS',
		                                 	configurationImportSummaries:  [
		                                 		{configurationName: 'anonymization', status: 'SUCCESS', errorCode: null}
		                                 	]
		                                 }
		                                 """));

		testImportedConfiguration("anonymization", "anonymization:\n  param1: 42\n");
	}

	@Test
	public void importConfigurationsJSON() throws Exception {
		final String configuration = """
		                             {
		                                "anonymization": {
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

		testImportedConfiguration("anonymization", "anonymization:\n  param1: 42\n");
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

		testImportedConfiguration("anonymization", "anonymization:\n  param1: 42\n");
		testImportedConfiguration("invalid_name", null);
	}

	@Test
	public void importConfigurationsSelected() throws Exception {
		final String configuration = """
		                             anonymization:
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

		testImportedConfiguration("anonymization", "anonymization:\n  param1: 42\n");
		testImportedConfiguration("invalid_name", null);
	}

	@Test
	void load() throws Exception {
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
		                                      .param("name", CONFIGURATION_NAME))
		       .andExpect(status().isOk())
		       .andExpect(content().string(config));
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
				                      .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				                      .code(200)
				                      .body("algorithms $dataset.original.numberHoldOutRows")
				                      .build());

		mockMvc.perform(get("/api/config/algorithm")
				                .param("configurationName", CONFIGURATION_NAME)
				                .param("definitionPath", "/algorithm"))
		       .andExpect(status().isOk())
		       .andExpect(content().string("algorithms 1"));
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
