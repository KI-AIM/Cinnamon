package de.kiaim.test.platform.service;

import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.repository.UserRepository;
import de.kiaim.platform.service.ProjectService;
import de.kiaim.platform.service.UserService;
import de.kiaim.test.platform.DatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectServiceTest extends DatabaseTest {

	@Autowired UserRepository userRepository;
	@Autowired ProjectService projectService;
	@Autowired UserService userService;

	@Test
	public void createProject() {
		var user = userService.save("email", "password");

		projectService.createProject(user);

		assertNotNull(user.getProject(), "No project has been created!");
		var project = user.getProject();

		assertEquals(1, project.getExecutions().size(), "Unexpected number of created executions!");
		assertTrue(project.getExecutions().containsKey(Step.EXECUTION), "No execution has been created for step 'EXECUTION'!");
		var exec = project.getExecutions().get(Step.EXECUTION);

		assertEquals(exec.getStep(), Step.EXECUTION, "Unexpected step!");
		assertEquals(ProcessStatus.NOT_STARTED, exec.getStatus(), "Unexpected process status!");
		assertNull(exec.getCurrentStep(), "No step has been created!");
		assertEquals(2, exec.getProcesses().size(), "Unexpected number of processes!");
		var firstProcess = exec.getProcesses().get(Step.EXECUTION.getProcesses().get(0));

		assertEquals(firstProcess.getExternalProcessStatus(), ProcessStatus.NOT_STARTED , "Unexpected status!");
	}

	@Test
	public void getExistingProject() {
		final UserEntity user = getTestUser();
		final ProjectEntity initialProject = new ProjectEntity();
		initialProject.getStatus().setCurrentStep(Step.VALIDATION);
		user.setProject(initialProject);
		userRepository.save(user);

		final ProjectEntity project = projectService.getProject(user);

		assertEquals(initialProject.getId(), project.getId(), "The returned project is not equal to the users project!");
		assertEquals(project.getStatus().getCurrentStep(), Step.VALIDATION, "The initial status is wrong!");
	}

}
