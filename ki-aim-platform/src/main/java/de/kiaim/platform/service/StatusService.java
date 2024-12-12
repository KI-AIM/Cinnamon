package de.kiaim.platform.service;

import de.kiaim.platform.model.configuration.KiAimConfiguration;
import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class for modifying the project status.
 */
@Service
public class StatusService {

	private final KiAimConfiguration kiAimConfiguration;

	private final ProjectRepository projectRepository;

	public StatusService(final KiAimConfiguration kiAimConfiguration, final ProjectRepository projectRepository) {
		this.kiAimConfiguration = kiAimConfiguration;
		this.projectRepository = projectRepository;
	}

	/**
	 * Sets the current status of the given project to the given status.
	 * Updates the flag for external processing based on the given step.
	 * If external processing is required, sets it to false, otherwise to true.
	 *
	 * @param project The project to be updated.
	 * @param currentStep The new step.
	 */
	@Transactional
	public void updateCurrentStep(final ProjectEntity project, final Step currentStep) {
		final var processStatus = kiAimConfiguration.getStages().containsKey(currentStep) && !kiAimConfiguration.getStages().get(currentStep).getJobs().isEmpty()
		                          ? ProcessStatus.NOT_STARTED
		                          : ProcessStatus.NOT_REQUIRED;
		updateStatus(project, currentStep, processStatus);
	}

	/**
	 * Sets the current status of the given project to the given status.
	 * @param project The project to be updated.
	 * @param currentStep The new step.
	 * @param externalProcessStatus The status of the external process.
	 */
	@Transactional
	public void updateStatus(final ProjectEntity project, final Step currentStep,
	                         final ProcessStatus externalProcessStatus) {
		project.getStatus().setCurrentStep(currentStep);
		projectRepository.save(project);
	}

}
