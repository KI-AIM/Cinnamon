package de.kiaim.cinnamon.test.platform.controller;

import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.test.platform.ControllerTest;
import de.kiaim.cinnamon.test.util.WithMockWebServer;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
}
