package de.kiaim.test.platform.controller;

import de.kiaim.platform.config.StepConfiguration;
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
		final var stepConfig = objectMapper.readValue(config, StepConfiguration.class);
		assertEquals("https://anonymization.de", stepConfig.getUrl(), "Unexpected URL!");
	}

	@Test
	public void getInvalidName() throws Exception {
		final var config = mockMvc.perform(get("/api/step/invalidStepName"))
		                          .andExpect(status().isBadRequest())
		                          .andExpect(errorMessage("The step 'invalidStepName' is not defined!"));
	}
}
