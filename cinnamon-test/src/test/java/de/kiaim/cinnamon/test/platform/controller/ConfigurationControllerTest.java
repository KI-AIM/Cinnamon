package de.kiaim.cinnamon.test.platform.controller;

import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import de.kiaim.cinnamon.platform.model.entity.UserEntity;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.test.platform.ControllerTest;
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
@WithUserDetails("test_user")
class ConfigurationControllerTest extends ControllerTest {

	@Autowired private CinnamonConfiguration cinnamonConfiguration;

	@Autowired
	ProjectService projectService;

	@Test
	void info() throws Exception {
		mockMvc.perform(get("/api/config/info")
				                .param("name", CONFIGURATION_NAME))
		       .andExpect(status().isOk())
		       .andExpect(content().json("{processes: [{job: 'anonymization', skip: false}]}"));
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
	void getAvailableAlgorithmMissingParam() throws Exception {
		mockMvc.perform(get("/api/config/algorithm"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_3_2_1"));
	}
}
