package de.kiaim.test.platform.controller;

import de.kiaim.platform.model.enumeration.Mode;
import de.kiaim.test.platform.ControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithUserDetails("test_user")
public class ProjectControllerTest extends ControllerTest {

	@Test
	public void createProject() throws Exception {
		mockMvc.perform(post("/api/project")
				                .contentType(MediaType.MULTIPART_FORM_DATA)
				                .param("mode", Mode.EXPERT.name()))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.mode").value("EXPERT"))
		       .andExpect(jsonPath("$.currentStep").value("UPLOAD"));
	}

	@Test
	public void getStatus() throws Exception {
		mockMvc.perform(get("/api/project/status"))
		       .andExpect(status().isOk())
				.andExpect(jsonPath("$.currentStep").value("WELCOME"));
	}

}
