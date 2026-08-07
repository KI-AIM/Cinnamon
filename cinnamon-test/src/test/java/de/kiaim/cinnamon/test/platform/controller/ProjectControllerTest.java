package de.kiaim.cinnamon.test.platform.controller;

import de.kiaim.cinnamon.platform.model.enumeration.Step;
import de.kiaim.cinnamon.platform.repository.ProjectRepository;
import de.kiaim.cinnamon.test.platform.ControllerTest;
import de.kiaim.cinnamon.test.util.ProjectConfigurationTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WithUserDetails("test_user")
public class ProjectControllerTest extends ControllerTest {

	@Autowired
	ProjectRepository repository;

	@BeforeEach
	public void setUpProjectConfiguration() {
		testProject.getProjectConfiguration()
		           .setMetricConfiguration(ProjectConfigurationTestHelper.generateMetricConfiguration());
		projectService.saveProject(testProject);
	}

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ getProject ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void getProject() throws Exception {
		mockMvc.perform(get("/api/project/" + testProject.getExternalId()))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                   id: '%s',
		                                   name: 'Test Project'
		                                 }
		                                 """.formatted(testProject.getExternalId())));
	}

	@Test
	public void getProjectInvalidIdFormat() throws Exception {
		mockMvc.perform(get("/api/project/INVALID_ID"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_1_11_3"))
		       .andExpect(errorMessage("Invalid project ID format"));
	}

	@Test
	public void getProjectNotFound() throws Exception {
		mockMvc.perform(get("/api/project/00000000-0000-0000-0000-000000000000"))
		       .andExpect(status().isNotFound())
		       .andExpect(errorCode("PLATFORM_1_17_1"))
		       .andExpect(errorMessage("Project with ID 00000000-0000-0000-0000-000000000000 not found"));
	}

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ deleteProject ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void deleteProject() throws Exception {
		mockMvc.perform(delete("/api/project/" + testProject.getExternalId())
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE)
				                .param("username", "test_user")
				                .param("password", "changeme"))
		       .andExpect(status().isOk());

		assertFalse(repository.existsById(testProject.getId()));
	}

	@Test
	public void deleteProjectWrongCredentials() throws Exception {
		mockMvc.perform(delete("/api/project/" + testProject.getExternalId())
				                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE)
				                .param("username", "test_user")
				                .param("password", "wrongpassword"))
		       .andExpect(status().isForbidden())
		       .andExpect(errorCode("PLATFORM_1_12_2"));

		assertTrue(repository.existsById(testProject.getId()));
	}

	// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ getStatus ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

	@Test
	public void getStatus() throws Exception {
		mockMvc.perform(get("/api/project/" + testProject.getExternalId() + "/status"))
		       .andExpect(status().isOk())
				.andExpect(jsonPath("$.currentStep").value("WELCOME"));
	}

	@Test
	public void confirm() throws Exception {
		mockMvc.perform(post("/api/project/" + testProject.getExternalId() + "/step")
				                .contentType(MediaType.MULTIPART_FORM_DATA)
				                .param("step", "TECHNICAL_EVALUATION"))
		       .andExpect(status().isOk());
		var updateTestProject = getTestProject();
		assertEquals(Step.TECHNICAL_EVALUATION, updateTestProject.getStatus().getCurrentStep());
	}

	@Test
	public void resetInvalidTarget() throws Exception {
		mockMvc.perform(delete("/api/project/" + testProject.getExternalId() + "/reset")
				                .queryParam("target", "INVALID.RESOURCE"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorCode("PLATFORM_1_11_2"))
		       .andExpect(errorMessage("The first part of the resource selector 'INVALID.RESOURCE' is not a valid key!"));
	}

	@Test
	public void getProjectConfiguration() throws Exception {
		mockMvc.perform(get("/api/project/" + testProject.getExternalId() + "/configuration"))
		       .andExpect(status().isOk())
		       .andExpect(content().json("""
		                                 {
		                                   projectName: 'Test Project',
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

		mockMvc.perform(put("/api/project/" + testProject.getExternalId() + "/configuration")
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

		mockMvc.perform(put("/api/project/" + testProject.getExternalId() + "/configuration")
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
		mockMvc.perform(get("/api/project/" + testProject.getExternalId() + "/resultFile")
				                .param("executionStepName", "evaluation")
				                .param("processStepName", "technical_evaluation")
				                .param("name", "invalidFile.txt"))
		       .andExpect(status().isBadRequest())
		       .andExpect(errorMessage("The file 'invalidFile.txt' could not be found!"));
	}
}
