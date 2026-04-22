package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.model.configuration.project.ProjectConfigurationDTO;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.configuration.Stage;
import de.kiaim.cinnamon.platform.model.configuration.Job;
import de.kiaim.cinnamon.platform.model.entity.*;
import de.kiaim.cinnamon.platform.model.enumeration.Mode;
import de.kiaim.cinnamon.platform.model.enumeration.Step;
import de.kiaim.cinnamon.platform.model.mapper.ProjectConfigurationMapper;
import de.kiaim.cinnamon.platform.repository.ProjectRepository;
import de.kiaim.cinnamon.platform.repository.UserRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class for managing projects.
 */
@Service
@Log4j2
public class ProjectService {

	private final CinnamonConfiguration cinnamonConfiguration;

	private final ProjectRepository projectRepository;
	private final UserRepository userRepository;

	private final ProjectConfigurationMapper projectConfigurationMapper;

	private final DatabaseService databaseService;
	private final ProcessService processService;
	private final StepService stepService;

	public ProjectService(
			final CinnamonConfiguration cinnamonConfiguration,
			final ProjectRepository projectRepository,
			final UserRepository userRepository,
			final ProjectConfigurationMapper projectConfigurationMapper,
			final DatabaseService databaseService,
			final ProcessService processService,
			final StepService stepService
	) {
		this.cinnamonConfiguration = cinnamonConfiguration;
		this.projectRepository = projectRepository;
		this.userRepository = userRepository;
		this.projectConfigurationMapper = projectConfigurationMapper;
		this.databaseService = databaseService;
		this.processService = processService;
		this.stepService = stepService;
	}

	/**
	 * Checks if the given user has a project.
	 *
	 * @param user The user to check.
	 * @return If the user ha a project.
	 */
	public boolean hasProject(final UserEntity user) {
		final UserEntity user2 = userRepository.findById(user.getEmail()).get();
		return user2.getProject() != null;
	}

	/**
	 * Creates and returns a new project for the given user if they do not have one.
	 * Otherwise, returns the existing project.
	 * Creates a random seed.
	 *
	 * @param user The user.
	 * @return The projects of the user.
	 * @throws InternalApplicationConfigurationException If a referenced step is not configured.
	 */
	@Transactional
	public ProjectEntity createProject(final UserEntity user) throws InternalApplicationConfigurationException {
		return createProject(user, System.currentTimeMillis());
	}

	/**
	 * Creates and returns a new project for the given user if they do not have one.
	 * Otherwise, returns the existing project.
	 *
	 * @param user        The user.
	 * @param projectSeed The seed used for the project.
	 * @return The projects of the user.
	 * @throws InternalApplicationConfigurationException If a referenced step is not configured.
	 */
	@Transactional
	public ProjectEntity createProject(final UserEntity user,
	                                   final long projectSeed) throws InternalApplicationConfigurationException {
		if (hasProject(user)) {
			return user.getProject();
		}

		final ProjectEntity project = createProject(projectSeed);
		user.setProject(project);
		// TODO change if projects are decoupled form users
		project.getProjectConfiguration().setProjectName(user.getEmail());

		log.debug("Created project for user '{}'", user.getEmail());
		return userRepository.save(user).getProject();
	}

	/**
	 * Creates a new empty project that is not associated to any user.
	 *
	 * @param projectSeed The seed used for the projects.
	 * @return The project.
	 * @throws InternalApplicationConfigurationException If a referenced step is not configured.
	 */
	public ProjectEntity createProject(final long projectSeed) throws InternalApplicationConfigurationException {
		final ProjectEntity project = new ProjectEntity(projectSeed);

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

	/**
	 * Returns the project of the user.
	 * Creates a new project, if the user does not have one.
	 * TODO: Add projectId parameter if multiple projects are supported
	 *
	 * @param user The user of the project.
	 * @return The project.
	 */
	@Transactional
	public ProjectEntity getProject(final UserEntity user) {
		if (!hasProject(user)) {
			throw new RuntimeException("No project");
		}

		final UserEntity user2 = userRepository.findById(user.getEmail()).get();
		return user2.getProject();
	}

	/**
	 * Deletes the project of the given user.
	 *
	 * @param user The user.
	 * @throws BadStateException                   If a process of the stage is running.
	 * @throws InternalDataSetPersistenceException If the data set could not be deleted due to an internal error.
	 */
	@Transactional
	public void deleteProject(final UserEntity user)
			throws BadStateException, InternalDataSetPersistenceException {
		if (hasProject(user)) {
			final ProjectEntity p = getProject(user);
			resetEntireProject(p);
			projectRepository.deleteById(p.getId());
			user.setProject(null);
			log.debug("Deleted project for user '{}'", user.getEmail());
		}
	}

	/**
	 * Restes the data in the project to the given target.
	 * The target can be 'original' to make the data configuration modifiable
	 * or in the form 'pipeline.{stage}' to reset the data of a stage.
	 *
	 * @param project The project.
	 * @param target  The target.
	 * @throws BadStateException                   If a process of the stage is running.
	 * @throws BadStepNameException                If no configuration could be found.
	 * @throws InternalDataSetPersistenceException If a dataset table could not be deleted.
	 */
	@Transactional
	public void resetProject(final ProjectEntity project, @Nullable final String target)
			throws BadStateException, BadStepNameException, InternalDataSetPersistenceException, BadArgumentException {

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
		userRepository.save(project.getUser());
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
	 *
	 * @param project The project to reset.
	 * @throws BadStateException                   If a process of the stage is running.
	 * @throws InternalDataSetPersistenceException If the data set could not be deleted due to an internal error.
	 */
	private void resetEntireProject(final ProjectEntity project)
			throws BadStateException, InternalDataSetPersistenceException {
		databaseService.deleteOriginalData(project);
		processService.deletePipeline(project);
	}
}
