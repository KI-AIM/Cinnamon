package de.kiaim.cinnamon.test.platform.controller;

import de.kiaim.cinnamon.test.platform.ControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class RootControllerTest extends ControllerTest {

	@Value("${cinnamon.version}")
	private String version;

	@Test
	public void getConfig() throws Exception {
		mockMvc.perform(get("/config.json"))
		       .andExpect(status().isOk())
		       .andExpect(content().json("{isDemoInstance: false, version: " + version + "}"))
		       .andExpect(jsonPath("$.demoInstance").doesNotExist());
	}

}
