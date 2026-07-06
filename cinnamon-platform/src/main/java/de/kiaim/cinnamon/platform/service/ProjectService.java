package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.model.configuration.project.ProjectConfigurationDTO;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.configuration.Stage;
import de.kiaim.cinnamon.platform.model.configuration.Job;
import de.kiaim.cinnamon.platform.model.dto.ProjectInfo;
import de.kiaim.cinnamon.platform.model.entity.*;
import de.kiaim.cinnamon.platform.model.enumeration.Mode;
import de.kiaim.cinnamon.platform.model.enumeration.Step;
import de.kiaim.cinnamon.platform.model.mapper.ProjectConfigurationMapper;
import de.kiaim.cinnamon.platform.repository.ProjectRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service class for managing projects.
 */
@Service
@Log4j2
public class ProjectService {

	public static final int GEN_EXTERNAL_ID_MAX_RETRIES = 10;

	private final CinnamonConfiguration cinnamonConfiguration;

	private final ProjectRepository projectRepository;

	private final ProjectConfigurationMapper projectConfigurationMapper;

	private final DatabaseService databaseService;
	private final ProcessService processService;
	private final StepService stepService;

	public ProjectService(
			final CinnamonConfiguration cinnamonConfiguration,
			final ProjectRepository projectRepository,
			final ProjectConfigurationMapper projectConfigurationMapper,
			final DatabaseService databaseService,
			final ProcessService processService,
			final StepService stepService
	) {
		this.cinnamonConfiguration = cinnamonConfiguration;
		this.projectRepository = projectRepository;
		this.projectConfigurationMapper = projectConfigurationMapper;
		this.databaseService = databaseService;
		this.processService = processService;
		this.stepService = stepService;
	}

	/**
	 * Creates a new empty project that is not associated to any user.
	 *
	 * @param projectSeed The seed used for the projects.
	 * @return The project.
	 * @throws InternalApplicationConfigurationException If a referenced step is not configured.
	 * @throws InternalErrorException                    If the project could not be created.
	 */
	public ProjectEntity createProject(final long projectSeed, final String projectName)
			throws InternalApplicationConfigurationException, InternalErrorException {
		final ProjectEntity project = new ProjectEntity(projectSeed);
		project.setExternalId(generateUUID());
		project.getProjectConfiguration().setProjectName(projectName);

		final PipelineEntity pipeline = new PipelineEntity();
		project.addPipeline(pipeline);

		// Create entities for external processes
		for (final String stageName : cinnamonConfiguration.getPipeline().getStages()) {
			if (!cinnamonConfiguration.getStages().containsKey(stageName)) {
				throw new InternalApplicationConfigurationException(
						InternalApplicationConfigurationException.MISSING_STAGE_CONFIGURATION,
						"No configuration for stage '" + stageName + "'!");
			}

			final Stage stageConfiguration = cinnamonConfiguration.getStages().get(stageName);
			final ExecutionStepEntity stage = new ExecutionStepEntity();

			for (final String jobName : stageConfiguration.getJobs()) {
				if (!cinnamonConfiguration.getSteps().containsKey(jobName)) {
					throw new InternalApplicationConfigurationException(
							InternalApplicationConfigurationException.MISSING_STEP_CONFIGURATION,
							"No configuration for step '" + jobName + "'!");
				}

				final Job stepConfiguration = cinnamonConfiguration.getSteps().get(jobName);
				ExternalProcessEntity job = switch (stepConfiguration.getStepType()) {
					case DATA_PROCESSING -> new DataProcessingEntity();
					case EVALUATION -> new EvaluationProcessingEntity();
				};

				job.setEndpoint(stepConfiguration.getExternalServerEndpointIndex());
				job.setJob(stepConfiguration);
				stage.addProcess(job);
			}

			pipeline.addStage(stageConfiguration, stage);
		}

		return project;
	}

	/**
	 * Saves the given project entity.
	 *
	 * @param projectEntity Entity to be saved.
	 */
	@Transactional
	public ProjectEntity saveProject(final ProjectEntity projectEntity) {
		return projectRepository.save(projectEntity);
	}

	@Transactional(readOnly = true)
	public ProjectEntity getProject(final UserEntity user, final String projectId)
			throws BadArgumentException, BadProjectException {
		UUID workflowIdAsUUID;

		try {
			workflowIdAsUUID = UUID.fromString(projectId);
		} catch (final IllegalArgumentException e) {
			throw new BadArgumentException(BadArgumentException.INVALID_PROJECT_ID, "Invalid project ID format");
		}


		return getProject(user, workflowIdAsUUID);
	}

	@Transactional(readOnly = true)
	public ProjectEntity getProject(final UserEntity user, final UUID projectId) throws BadProjectException {
		final Optional<ProjectEntity> project = projectRepository.findByExternalId(projectId);
		if (project.isEmpty() || project.get().getUser() == null ||
		    !project.get().getUser().getUsername().equals(user.getUsername())) {
			throw new BadProjectException(BadProjectException.NOT_FOUND,
			                               "Project with ID " + projectId + " not found");
		}

		return project.get();
	}

	@Transactional(readOnly = true)
	public ProjectInfo getProjectInfo(final UserEntity user, final String projectId)
			throws BadArgumentException, BadProjectException {
		final ProjectEntity project = getProject(user, projectId);
		return getProjectInfo(project);
	}

	@Transactional(readOnly = true)
	public ProjectInfo getProjectInfo(final ProjectEntity project) {
		return new ProjectInfo(project.getExternalId().toString(), project.getProjectConfiguration().getProjectName());
	}

	/**
	 * Returns all workflows that have expired.
	 *
	 * @return List of expired workflows.
	 */
	@Transactional(readOnly = true)
	public List<ProjectEntity> getExpiredProjects() {
		final Timestamp expirationDate = new Timestamp(System.currentTimeMillis());
		return projectRepository.findAllByExpirationDateBefore(expirationDate);
	}

	@Transactional
	public void deleteProject(final ProjectEntity project)
			throws InternalDataSetPersistenceException, InternalInvalidStateException {
		final UserEntity user = project.getUser();
		deleteProject(user, project);
	}

	/**
	 * Deletes the project of the given user.
	 * If a pipeline in the project is running, the process is stopped.
	 *
	 * @param user The user.
	 * @throws InternalDataSetPersistenceException If the data set could not be deleted due to an internal error.
	 * @throws InternalInvalidStateException       If the running process has no server instance assigned.
	 */
	@Transactional
	public void deleteProject(final UserEntity user, final ProjectEntity project)
			throws InternalDataSetPersistenceException, InternalInvalidStateException {
		resetEntireProject(project);
		user.removeProject(project);
		log.debug("Deleted project for user '{}'", user.getUsername());
	}

	/**
	 * Restes the data in the project to the given target.
	 * The target can be 'original' to make the data configuration modifiable
	 * or in the form 'pipeline.{stage}' to reset the data of a stage.
	 *
	 * @param project The project.
	 * @param target  The target.
	 * @throws BadArgumentException                If the target is invalid.
	 * @throws BadStateException                   If a process of the stage is running.
	 * @throws BadStepNameException                If no configuration could be found.
	 * @throws InternalDataSetPersistenceException If a dataset table could not be deleted.
	 * @throws InternalInvalidStateException       If a process is running and the check for running processes failed.
	 */
	@Transactional
	public void resetProject(final ProjectEntity project, @Nullable final String target)
			throws BadArgumentException, BadStateException, BadStepNameException, InternalDataSetPersistenceException,
					       InternalInvalidStateException {
		if (processService.isPipelineRunning(project.getPipelines().get(0))) {
			throw new BadStateException(BadStateException.PROCESS_STARTED,
			                            "Cannot reset project while pipeline is running");
		}

		if (target == null || target.isBlank()) {
			resetEntireProject(project);
		} else {

			final String[] parts = target.split("\\.");

			if (parts[0].equals("original")) {
				processService.deletePipeline(project);

				if (project.getOriginalData().getDataSet() != null) {
					project.getOriginalData().getDataSet().setConfirmedData(false);
				}

				project.getConfigurations().clear();

				if (parts.length > 1) {
					if (parts[1].equals("dataset")) {
						databaseService.deleteOriginalDatasetIgnoreConfirmed(project);
					} else if (parts[1].equals("file")) {
						databaseService.deleteOriginalData(project);
					}
				}
			} else if (parts[0].equals("pipeline")) {
				final Stage stage = stepService.getStageConfiguration(parts[1]);
				processService.deleteStage(project, stage);
			} else {
				throw new BadArgumentException(BadArgumentException.INVALID_RESOURCE_KEY,
				                               "The first part of the resource selector '" + target +
				                               "' is not a valid key!");
			}
		}

		log.debug("Reset project to '{}'", target);

		projectRepository.save(project);
	}

	@Transactional
	public void setMode(final ProjectEntity project, final Mode mode) {
		project.getStatus().setMode(mode);
		projectRepository.save(project);
	}

	/**
	 * Sets the current step of the given project to the given step.
	 *
	 * @param project     The project to be updated.
	 * @param currentStep The new step.
	 */
	@Transactional
	public void updateCurrentStep(final ProjectEntity project, final Step currentStep) {
		project.getStatus().setCurrentStep(currentStep);
		projectRepository.save(project);
	}

	/**
	 * Returns a DTO of the project configuration of the given project.
	 *
	 * @param project The project.
	 * @return The DTO of the project configuration.
	 */
	public ProjectConfigurationDTO exportProjectConfiguration(final ProjectEntity project) {
		return projectConfigurationMapper.toDto(project.getProjectConfiguration());
	}

	/**
	 * Updates the project configuration.
	 *
	 * @param project       The project to be updated.
	 * @param configuration The new configuration.
	 */
	@Transactional
	public void updateProjectConfiguration(final ProjectEntity project, final ProjectConfigurationDTO configuration) {
		projectConfigurationMapper.updateEntity(project.getProjectConfiguration(), configuration);
	}

	/**
	 * Resets all data inside the given project.
	 * If a pipeline in the project is running, the process is stopped.
	 *
	 * @param project The project to reset.
	 * @throws InternalDataSetPersistenceException If the data set could not be deleted due to an internal error.
	 * @throws InternalInvalidStateException       If the running process has no server instance assigned.
	 */
	@Transactional
	public void resetEntireProject(final ProjectEntity project)
			throws InternalDataSetPersistenceException, InternalInvalidStateException {
		processService.deletePipeline(project);
		databaseService.deleteOriginalData(project);
	}

	/**
	 * Generates a unique workflow ID.
	 *
	 * @return The generated workflow ID.
	 * @throws InternalErrorException If the ID could not be generated after 10 retries.
	 */
	private UUID generateUUID() throws InternalErrorException {
		UUID uuid;
		for (int i = 0; i < GEN_EXTERNAL_ID_MAX_RETRIES; i++) {
			uuid = UUID.randomUUID();

			if (projectRepository.countByExternalId(uuid) == 0) {
				return uuid;
			}
		}

		throw new InternalErrorException(InternalErrorException.GEN_EXTERNAL_ID_MAX_RETRIES,
		                                 "Failed to generate a unique workflow ID after " +
		                                 GEN_EXTERNAL_ID_MAX_RETRIES+ " retries! Please try again later.");
	}

}
