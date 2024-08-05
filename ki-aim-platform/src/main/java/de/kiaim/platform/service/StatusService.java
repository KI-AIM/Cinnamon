package de.kiaim.platform.service;

import de.kiaim.platform.model.entity.ProjectEntity;
import de.kiaim.platform.model.enumeration.ProcessStatus;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.repository.ProjectRepository;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class for modifying the project status.
 */
@Service
public class StatusService {

	private final ProjectRepository projectRepository;
	private final SecurityExpressionHandler webSecurityExpressionHandler;

	public StatusService(ProjectRepository projectRepository, SecurityExpressionHandler webSecurityExpressionHandler) {
		this.projectRepository = projectRepository;
		this.webSecurityExpressionHandler = webSecurityExpressionHandler;
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
		updateStatus(project, currentStep,
		             currentStep.isHasExternalProcessing() ? ProcessStatus.NOT_STARTED : ProcessStatus.NOT_REQUIRED);
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
