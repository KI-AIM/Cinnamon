package de.kiaim.test.platform.controller;

import de.kiaim.platform.model.configuration.Stage;
import de.kiaim.platform.model.dto.StepConfigurationResponse;
import de.kiaim.test.platform.ControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithUserDetails("test_user")
public class StepControllerTest extends ControllerTest {

	@Test
	public void getStepConfiguration() throws Exception {
		final var config = mockMvc.perform(get("/api/step/anonymization"))
		                          .andExpect(status().isOk())
		                          .andReturn().getResponse().getContentAsString();
		final var stepConfig = objectMapper.readValue(config, StepConfigurationResponse.class);
		assertEquals("http://anonymization.de", stepConfig.getUrlClient(), "Unexpected URL!");
	}

	@Test
	public void getInvalidName() throws Exception {
		mockMvc.perform(get("/api/step/invalidStepName"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("No configuration with name 'invalidStepName' registered!"));
	}

	@Test
	public void getStageStage() throws Exception {
		final var config = mockMvc.perform(get("/api/step/stage/execution"))
		                          .andExpect(status().isOk())
		                          .andReturn().getResponse().getContentAsString();
		final var stageConfig = objectMapper.readValue(config, Stage.class);
		assertEquals(2, stageConfig.getJobs().size(), "Unexpected number of jobs!");
	}

	@Test
	public void getStageInvalidName() throws Exception {
		mockMvc.perform(get("/api/step/stage/invalidStepName"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("The step 'invalidStepName' is not defined!"));
	}
}
