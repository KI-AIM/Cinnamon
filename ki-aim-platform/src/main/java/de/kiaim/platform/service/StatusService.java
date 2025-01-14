package de.kiaim.platform.service;

import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class for modifying the project status.
 */
@Service
public class StatusService {

	private final ProjectRepository projectRepository;

	public StatusService(final ProjectRepository projectRepository) {
		this.projectRepository = projectRepository;
	}

	/**
	 * Sets the current step of the given project to the given step.
	 *
	 * @param project The project to be updated.
	 * @param currentStep The new step.
	 */
	@Transactional
	public void updateCurrentStep(final ProjectEntity project, final Step currentStep) {
		project.getStatus().setCurrentStep(currentStep);
		projectRepository.save(project);
	}

}
