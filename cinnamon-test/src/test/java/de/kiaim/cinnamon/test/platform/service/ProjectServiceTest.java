package de.kiaim.cinnamon.test.platform.service;

import de.kiaim.cinnamon.model.enumeration.ProcessStatus;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.entity.*;
import de.kiaim.cinnamon.platform.model.enumeration.Step;
import de.kiaim.cinnamon.platform.repository.UserRepository;
import de.kiaim.cinnamon.platform.service.ProjectService;
import de.kiaim.cinnamon.platform.service.UserService;
import de.kiaim.cinnamon.test.platform.DatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectServiceTest extends DatabaseTest {

	@Autowired CinnamonConfiguration cinnamonConfiguration;
	@Autowired UserRepository userRepository;
	@Autowired ProjectService projectService;
	@Autowired UserService userService;

	@Test
	public void createProject() {
		var user = userService.save("email", "password");

		assertDoesNotThrow(() -> projectService.createProject(user));

		assertNotNull(user.getProject(), "No project has been created!");
		var project = user.getProject();

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
		assertEquals(ProcessStatus.NOT_STARTED, exec.getStatus(), "Unexpected process status!");
		assertNull(exec.getCurrentProcessIndex(), "No step has been created!");
		assertEquals(2, exec.getProcesses().size(), "Unexpected number of processes!");
		var firstProcess = exec.getProcess(0);

		assertEquals(firstProcess.getExternalProcessStatus(), ProcessStatus.NOT_STARTED , "Unexpected status!");
	}

	@Test
	public void getExistingProject() {
		final UserEntity user = getTestUser();
		ProjectEntity initialProject = new ProjectEntity();
		initialProject.getStatus().setCurrentStep(Step.VALIDATION);
		user.setProject(initialProject);
		initialProject = userRepository.save(user).getProject();

		final ProjectEntity project = projectService.getProject(user);

		assertEquals(initialProject.getId(), project.getId(), "The returned project is not equal to the users project!");
		assertEquals(project.getStatus().getCurrentStep(), Step.VALIDATION, "The initial status is wrong!");
	}

}
