package de.kiaim.test.platform.controller;

import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.test.platform.ControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@WithUserDetails("test_user")
class ConfigurationControllerTest extends ControllerTest {

	@Test
	void store() throws Exception {
		final String configName = "testConfigName";
		final String config = """
				configurations:
				- index: 0
				  name: "column0_boolean"
				  type: "BOOLEAN"
				  scale: "NOMINAL"
				  configurations: []
				""";

		mockMvc.perform(post("/api/config")
				                .param("name", configName)
				                .contentType(MediaType.TEXT_PLAIN_VALUE)
				                .content(config))
		       .andExpect(status().isOk());

		final UserEntity user = getTestUser();
		final ProjectEntity dataConfiguration = user.getProject();
		assertNotNull(dataConfiguration, "The configuration has not been created!");
		assertTrue(dataConfiguration.getConfigurations().containsKey(configName),
		           "The configuration has not been stored correctly under the user!");
		assertEquals(config, dataConfiguration.getConfigurations().get(configName),
		             "The configuration has not been stored correctly!");
	}

	@Test
	void load() throws Exception {
		final String configName = "testConfigName";
		final String config = """
				configurations:
				- index: 0
				  name: "column0_boolean"
				  type: "BOOLEAN"
				  scale: "NOMINAL"
				  configurations: []
				""";

		storeConfiguration(configName, config);

		mockMvc.perform(MockMvcRequestBuilders.get("/api/config")
		                                      .param("name", configName))
		       .andExpect(status().isOk())
		       .andExpect(content().string(config));
	}

	@Test
	void loadNoConfiguration() throws Exception {
		final String configName = "testConfigName";

		mockMvc.perform(MockMvcRequestBuilders.get("/api/config")
		                                      .param("name", configName))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("User has no configuration!"));
	}

	@Test
	void loadInvalidName() throws Exception {
		final String configName = "testConfigName";
		final String invalidConfigName = "invalidConfigName";
		final String config = """
				configurations:
				- index: 0
				  name: "column0_boolean"
				  type: "BOOLEAN"
				  scale: "NOMINAL"
				  configurations: []
				""";

		storeConfiguration(configName, config);

		mockMvc.perform(MockMvcRequestBuilders.get("/api/config")
		                                      .param("name", invalidConfigName))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("User has no configuration with the name '" + invalidConfigName + "'!"));
	}
}