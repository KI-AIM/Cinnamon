package de.kiaim.platform.service;

import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatusService {

	private final ProjectRepository projectRepository;

	public StatusService(ProjectRepository projectRepository) {
		this.projectRepository = projectRepository;
	}

	@Transactional
	public void updateStatus(final ProjectEntity project, final Step currentStep, final boolean finishedExternalProcessing) {
		// TODO error handling for invalid projectId
		project.getStatus().setCurrentStep(currentStep);
		project.getStatus().setFinishedExternalProcessing(finishedExternalProcessing);
		projectRepository.save(project);
	}

}
