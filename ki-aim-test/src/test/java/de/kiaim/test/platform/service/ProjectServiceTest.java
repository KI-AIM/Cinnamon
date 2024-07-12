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

		assertEquals(initialProject.getId(), project.getId(), "The returned project is not equal to the users project!");
		assertEquals(project.getStatus().getCurrentStep(), Step.VALIDATION, "The initial status is wrong!");
	}

}
