package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.model.enumeration.ProcessStatus;
import de.kiaim.cinnamon.model.enumeration.StageStatus;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.configuration.Stage;
import de.kiaim.cinnamon.platform.model.entity.*;
import de.kiaim.cinnamon.platform.model.enumeration.Step;
import de.kiaim.cinnamon.platform.repository.UserRepository;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.test.platform.DatabaseTest;
import de.kiaim.cinnamon.test.util.WithMockWebServer;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

@WithMockWebServer
public class ProjectServiceTest extends DatabaseTest {

	@Autowired CinnamonConfiguration cinnamonConfiguration;
	@Autowired UserRepository userRepository;
	@Autowired ProjectService projectService;
	@Autowired UserService userService;

	private MockWebServer mockBackEnd;

	@Test
	public void createProject() {
		var user = userService.save("email", "password");

		var project = assertDoesNotThrow(() -> projectService.createProject(user));

		assertEquals(1, user.getProjects().size(), "Unexpected number of created projects!");

		assertEquals(1, project.getPipelines().size(), "Unexpected  number of created pipelines!");
		var pipeline = project.getPipelines().get(0);
		assertEquals(2, pipeline.getStages().size(), "Unexpected number of created executions!");
		assertTrue(pipeline.containsStage(cinnamonConfiguration.getPipeline().getStageList().get(0)),
		           "No execution has been created for step 'EVALUATION'!");
		assertTrue(pipeline.containsStage(cinnamonConfiguration.getPipeline().getStageList().get(1)),
		           "No execution has been created for step 'EXECUTION'!");
		var stage = cinnamonConfiguration.getPipeline().getStageList().get(0);
		var exec = pipeline.getStageByStep(stage);

		assertEquals(exec.getStage(), stage, "Unexpected step!");
		assertEquals(StageStatus.NOT_STARTED, exec.getStatus(), "Unexpected process status!");
		assertNull(exec.getCurrentProcessIndex(), "No step has been created!");
		assertEquals(2, exec.getProcesses().size(), "Unexpected number of processes!");
		var firstProcess = exec.getProcess(0);

		assertEquals(ProcessStatus.NOT_STARTED, firstProcess.getExternalProcessStatus(), "Unexpected status!");
	}

	@Test
	public void getExistingProject() {
		final UserEntity user = getTestUser();
		ProjectEntity initialProject = assertDoesNotThrow(() -> projectService.createProject(0L));
		initialProject.getStatus().setCurrentStep(Step.VALIDATION);
		user.addProject(initialProject);
		initialProject = userRepository.save(user).getProject(initialProject.getExternalId());

		ProjectEntity finalInitialProject = initialProject;
		final ProjectEntity project = assertDoesNotThrow(
				() -> projectService.getProject(user, finalInitialProject.getExternalId()));

		assertEquals(initialProject.getId(), project.getId(), "The returned project is not equal to the users project!");
		assertEquals(Step.VALIDATION, project.getStatus().getCurrentStep(), "The initial status is wrong!");
	}

	@Test
	public void resetProject() {
		final Stage stage = cinnamonConfiguration.getPipeline().getStageList().get(0);
		final ProjectEntity project = createProject(stage, ProcessStatus.FINISHED);

		assertDoesNotThrow(() -> projectService.resetProject(project, null));
		ExecutionStepEntity executionStep = project.getPipelines().get(0).getStages().get(0);

		assertEquals(StageStatus.NOT_STARTED, executionStep.getStatus(), "Status should be NOT_STARTED");

		ExternalProcessEntity externalProcess = executionStep.getProcess(0);
		assertEquals(ProcessStatus.NOT_STARTED, externalProcess.getExternalProcessStatus(),
		             "Status should be NOT_STARTED");
		assertTrue(externalProcess.getResultFiles().isEmpty(), "Result files should be empty!");
		assertNull(externalProcess.getStatus(), "Status should be null!");
	}

	@Test
	public void resetProjectRunning() {
		final Stage stage = cinnamonConfiguration.getPipeline().getStageList().get(0);
		final ProjectEntity project = createProject(stage, ProcessStatus.RUNNING);

		mockBackEnd.enqueue(new MockResponse.Builder().code(200).build());

		BadStateException exception = assertThrows(BadStateException.class,
		                                           () -> projectService.resetProject(project, null));
		assertEquals("PLATFORM_1_8_1", exception.getErrorCode(), "Unexpected error code!");
	}

}
