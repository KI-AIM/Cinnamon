package de.kiaim.test.platform.service;


import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.repository.UserRepository;
import de.kiaim.platform.service.ProjectService;
import de.kiaim.test.platform.DatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectServiceTest extends DatabaseTest {

	@Autowired
	ProjectService projectService;

	@Autowired
	UserRepository userRepository;

	@Test
	public void getExistingProject() {
		final UserEntity user = getTestUser();
		final ProjectEntity initialProject = new ProjectEntity();
		initialProject.getStatus().setCurrentStep(Step.VALIDATION);
		user.setProject(initialProject);
		userRepository.save(user);

		final ProjectEntity project = projectService.getProject(user);

		assertEquals(initialProject, project, "The returned project is not equal to the users project!");
		assertEquals(project.getStatus().getCurrentStep(), Step.VALIDATION, "The initial status is wrong!");
	}

	@Test
	public void getNotExistingProject() {
		final UserEntity user = getTestUser();

		assertNull(user.getProject(), "The user shouldn't have a project in the beginning!");
		final ProjectEntity project = projectService.getProject(user);

		assertNotNull(user.getProject(), "The project has not been created!");
		assertEquals(user.getProject(), project, "The returned project is not equal to the users project!");
		assertEquals(project.getStatus().getCurrentStep(), Step.UPLOAD, "The initial status is wrong!");
		assertEquals(project.getStatus().getFinishedExternalProcessing(), true, "The initial step should not!");
	}

}
