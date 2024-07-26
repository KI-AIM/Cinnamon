package de.kiaim.platform.service;

import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.entity.StatusEntity;
import de.kiaim.platform.model.entity.UserEntity;
import de.kiaim.platform.model.enumeration.Mode;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class for managing projects.
 */
@Service
public class ProjectService {

	private final UserRepository userRepository;

	public ProjectService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/**
	 * Returns the project of the user.
	 * Creates a new project, if the user does not have one.
	 * TODO: Add projectId parameter if multiple projects are supported
	 * TODO: Add separate function to create a new project
	 *
	 * @param user The user of the project.
	 * @return The project.
	 */
	@Transactional
	public ProjectEntity getProject(final UserEntity user) {
		final UserEntity user2 = userRepository.findById(user.getEmail()).get();

		ProjectEntity project = user2.getProject();
		if (project == null) {
			project = new ProjectEntity();
			user.setProject(project);
			userRepository.save(user2);
		}
		return project;
	}

	@Transactional
	public void setMode(final ProjectEntity project, final Mode mode) {
		project.getStatus().setMode(mode);
		project.getStatus().setCurrentStep(Step.UPLOAD);
		userRepository.save(project.getUser());
	}

}
