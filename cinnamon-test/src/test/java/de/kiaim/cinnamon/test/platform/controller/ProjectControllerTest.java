package de.kiaim.cinnamon.test.platform.controller;

import de.kiaim.cinnamon.platform.model.enumeration.Mode;
import de.kiaim.cinnamon.platform.model.enumeration.Step;
import de.kiaim.cinnamon.test.platform.ControllerTest;
import de.kiaim.cinnamon.test.util.ProjectConfigurationTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithUserDetails("test_user")
public class ProjectControllerTest extends ControllerTest {

	@BeforeEach
	public void setUpProjectConfiguration() {
		testProject.getProjectConfiguration()
		           .setMetricConfiguration(ProjectConfigurationTestHelper.generateMetricConfiguration());
		projectService.saveProject(testProject);
	}

	@Test
	public void createProject() throws Exception {
		mockMvc.perform(post("/api/project")
				                .contentType(MediaType.MULTIPART_FORM_DATA)
				                .param("mode", Mode.EXPERT.name()))
		       .andExpect(status().isOk())
		       .andExpect(jsonPath("$.mode").value("EXPERT"))
		       .andExpect(jsonPath("$.currentStep").value("WELCOME"));
	}

	@Test
	public void createProjectInvalidMode() throws Exception {
		final MockMultipartFile invalidParam = new MockMultipartFile("mode", "mode",
		                                                             MediaType.TEXT_PLAIN_VALUE,
		                                                             "EXPERT".getBytes());

		mockMvc.perform(multipart("/api/project")
				                .file(invalidParam)
				                .contentType(MediaType.MULTIPART_FORM_DATA))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_3_1_4"))
		       .andExpect(errorMessage("Parameter 'mode' must not be a file!"));
	}

	@Test
	public void getStatus() throws Exception {
		mockMvc.perform(get("/api/project/status"))
		       .andExpect(status().isOk())
				.andExpect(jsonPath("$.currentStep").value("WELCOME"));
	}

	@Test
	public void confirm() throws Exception {
		mockMvc.perform(post("/api/project/step")
				                .contentType(MediaType.MULTIPART_FORM_DATA)
				                .param("step", "TECHNICAL_EVALUATION"))
		       .andExpect(status().isOk());
		var updateTestProject = getTestProject();
		assertEquals(Step.TECHNICAL_EVALUATION, updateTestProject.getStatus().getCurrentStep());
	}

	@Test
	public void resetInvalidTarget() throws Exception {
		mockMvc.perform(delete("/api/project/reset")
				                .queryParam("target", "INVALID.RESOURCE"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_1_11_2"))
		       .andExpect(errorMessage("The first part of the resource selector 'INVALID.RESOURCE' is not a valid key!"));
	}

	@Test
	public void getProjectConfiguration() throws Exception {
		mockMvc.perform(get("/api/project/configuration"))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                   projectName: 'test_user',
		                                   contactMail: null,
		                                   contactUrl: null,
		                                   reportCreator: null,
		                                   metricConfiguration: {
		                                     colorScheme: 'Fluffy Unicorn',
		                                     useUserDefinedImportance: true,
		                                     userDefinedImportance: {
		                                       MetricA: 'IMPORTANT',
		                                       MetricB: 'ADDITIONAL',
		                                       MetricC: 'NOT_RELEVANT'
		                                     }
		                                   }
		                                 }
		                                 """));
	}

	@Test
	public void putProjectConfiguration() throws Exception {
		var dto = ProjectConfigurationTestHelper.generateProjectConfigurationDTO();
		var metricConfiguration = ProjectConfigurationTestHelper.generateMetricConfiguration();
		metricConfiguration.setColorScheme("TEST_COLOR_SCHEME");
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
		var dto = ProjectConfigurationTestHelper.generateProjectConfigurationDTO();
		dto.setMetricConfiguration(null);

		mockMvc.perform(put("/api/project/configuration")
				                .contentType(MediaType.APPLICATION_JSON)
				                .content(jsonMapper.writeValueAsString(dto)))
		       .andExpect(status().isBadRequest())
		       .andExpect(validationError("metricConfiguration", "The metric configuration must be present!"));

		var project = getTestProject();
		assertEquals(ProjectConfigurationTestHelper.generateMetricConfiguration(),
		             project.getProjectConfiguration().getMetricConfiguration(),
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
