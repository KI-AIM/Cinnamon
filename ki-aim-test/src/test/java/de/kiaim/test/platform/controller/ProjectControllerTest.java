package de.kiaim.test.platform.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import de.kiaim.platform.model.dto.ProjectConfigurationDTO;
import de.kiaim.platform.model.enumeration.Mode;
import de.kiaim.test.platform.ControllerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithUserDetails("test_user")
public class ProjectControllerTest extends ControllerTest {

	private static final String ORIGINAL_METRIC_CONFIGURATION = "{\"mean\": \"NOT_RELEVANT\"}";
	private Object originalMetricConfiguration = null;

	@BeforeEach
	public void setUpProjectConfiguration() throws JsonProcessingException {
		originalMetricConfiguration = jsonMapper.readValue(ORIGINAL_METRIC_CONFIGURATION, Object.class);
		testProject.getProjectConfiguration().setMetricConfiguration(originalMetricConfiguration);
		projectService.saveProject(testProject);
	}

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

	@Test
	public void getProjectConfiguration() throws Exception {
		mockMvc.perform(get("/api/project/configuration"))
		       .andExpect(status().isOk())
		       .andExpect(content().json("{metricConfiguration: {mean: 'NOT_RELEVANT'}}"));
	}

	@Test
	public void putProjectConfiguration() throws Exception {
		var metricConfiguration = jsonMapper.readValue("{\"mean\": \"IMPORTANT\"}", Object.class);
		var dto = new ProjectConfigurationDTO();
		dto.setMetricConfiguration(metricConfiguration);

		mockMvc.perform(put("/api/project/configuration")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(jsonMapper.writeValueAsString(dto)))
		       .andExpect(status().isOk());

		var project = getTestProject();
		assertEquals(metricConfiguration, project.getProjectConfiguration().getMetricConfiguration());
	}

	@Test
	public void putProjectConfigurationMissing() throws Exception {
		var dto = new ProjectConfigurationDTO();
		dto.setMetricConfiguration(null);

		mockMvc.perform(put("/api/project/configuration")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(jsonMapper.writeValueAsString(dto)))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("metricConfiguration", "The metric configuration must be present!"));

		var project = getTestProject();
		assertEquals(originalMetricConfiguration, project.getProjectConfiguration().getMetricConfiguration(),
		             "The metric configuration should not have changed!");
	}

	@Test
	public void getInvalidResultFile() throws Exception {
		mockMvc.perform(get("/api/project/resultFile")
				                .param("executionStepName", "evaluation")
				                .param("processStepName", "technical_evaluation")
				                .param("name", "invalidFile.txt"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("The file 'invalidFile.txt' could not be found!"));
	}
}
