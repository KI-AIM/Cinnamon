package de.kiaim.cinnamon.platform.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.data.DataRow;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.configuration.Stage;
import de.kiaim.cinnamon.platform.model.configuration.Job;
import de.kiaim.cinnamon.platform.exception.InternalApplicationConfigurationException;
import de.kiaim.cinnamon.platform.exception.InternalDataSetPersistenceException;
import de.kiaim.cinnamon.platform.exception.InternalIOException;
import de.kiaim.cinnamon.platform.model.entity.*;
import de.kiaim.cinnamon.platform.model.enumeration.HoldOutSelector;
import de.kiaim.cinnamon.platform.model.enumeration.Mode;
import de.kiaim.cinnamon.platform.model.enumeration.Step;
import de.kiaim.cinnamon.platform.repository.ProjectRepository;
import de.kiaim.cinnamon.platform.repository.UserRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service class for managing projects.
 */
@Service
public class ProjectService {

	private final ObjectMapper yamlMapper;
	private final CinnamonConfiguration cinnamonConfiguration;
	private final ProjectRepository projectRepository;
	private final UserRepository userRepository;
	private final DatabaseService databaseService;
	private final StepService stepService;

	public ProjectService(final ObjectMapper yamlMapper, final CinnamonConfiguration cinnamonConfiguration,
	                      final ProjectRepository projectRepository, final UserRepository userRepository,
	                      final DatabaseService databaseService,
	                      final StepService stepService) {
		this.yamlMapper = yamlMapper;
		this.cinnamonConfiguration = cinnamonConfiguration;
		this.projectRepository = projectRepository;
		this.userRepository = userRepository;
		this.databaseService = databaseService;
		this.stepService = stepService;
	}

	/**
	 * Checks if the given user has a project.
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
	public ProjectEntity createProject(final UserEntity user, final long projectSeed) throws InternalApplicationConfigurationException {
		if (hasProject(user)) {
			return user.getProject();
		}

		final ProjectEntity project = createProject(projectSeed);
		user.setProject(project);

		userRepository.save(user);

		return project;
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
	 * @param projectEntity Entity to be saved.
	 */
	@Transactional
	public void saveProject(final ProjectEntity projectEntity) {
		projectRepository.save(projectEntity);
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

	@Transactional
	public void setMode(final ProjectEntity project, final Mode mode) {
		project.getStatus().setMode(mode);
		userRepository.save(project.getUser());
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

	/**
	 * Writes a ZIP to the given OutputStream containing the data set and data configuration of the given project and the given configuration.
	 *
	 * @param project      The project of the data set.
	 * @param outputStream The OutputStream to write to.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 * @throws InternalIOException                 If the request body could not be created.
	 */
	@Transactional
	public void createZipFile(final ProjectEntity project, final OutputStream outputStream)
			throws InternalDataSetPersistenceException, InternalIOException {
		try (final ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {

			// Add original data set
			final DataSetEntity dataSetEntity = project.getOriginalData().getDataSet();
			if (dataSetEntity != null) {
				// Add data configuration
				final ZipEntry attributeConfigZipEntry = new ZipEntry("attribute_config.yaml");
				zipOut.putNextEntry(attributeConfigZipEntry);
				yamlMapper.writer().without(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
				          .writeValue(zipOut, dataSetEntity.getDataConfiguration());
				zipOut.closeEntry();

				// Add data set
				if (dataSetEntity.isStoredData()) {
					final DataSet dataSet = databaseService.exportDataSet(dataSetEntity, HoldOutSelector.ALL);
					addCsvToZip(zipOut, dataSet, "original");
				}

				// Add statistics
				final LobWrapperEntity statistics = project.getOriginalData().getDataSet().getStatistics();
				if (statistics != null) {
					final ZipEntry statisticsEntry = new ZipEntry("statistics.yaml");
					zipOut.putNextEntry(statisticsEntry);
					zipOut.write(statistics.getLob());
					zipOut.closeEntry();
				}
			}

			// Add results
			Map<String, Integer> zipEntryCounter = new HashMap<>();
			for (final PipelineEntity pipeline : project.getPipelines()) {
				for (final ExecutionStepEntity executionStep : pipeline.getStages()) {
					for (final ExternalProcessEntity externalProcess : executionStep.getProcesses()) {
						// Add configuration
						if (externalProcess.getConfigurationString() != null) {
							final String configurationName = stepService.getExternalServerEndpointConfiguration(externalProcess.getJob())
							                                            .getConfigurationName();
							final ZipEntry configZipEntry = new ZipEntry(configurationName + ".yaml");
							zipOut.putNextEntry(configZipEntry);
							zipOut.write(externalProcess.getConfigurationString().getBytes());
							zipOut.closeEntry();
						}

						// Add data set
						if (externalProcess instanceof DataProcessingEntity dataProcessing ) {
							if (dataProcessing.getDataSet() != null && dataProcessing.getDataSet().isStoredData()) {
								addCsvToZip(zipOut, databaseService.exportDataSet(dataProcessing.getDataSet(), HoldOutSelector.ALL),
								            dataProcessing.getDataSet().getProcessed().stream().map(Job::getName)
								                          .collect(Collectors.joining("-")));
							}
						}

						// Add additional files
						for (final var entry : externalProcess.getResultFiles().entrySet()) {
							String entryKey = entry.getKey();
							if (zipEntryCounter.containsKey(entryKey)) {
								var count = zipEntryCounter.get(entryKey);
								entryKey = entryKey.substring(0, entryKey.lastIndexOf('.')) + "_" + count +
								           entryKey.substring(entryKey.lastIndexOf('.'));
								zipEntryCounter.put(entryKey, count + 1);
							} else {
								zipEntryCounter.put(entryKey, 1);
							}

							final ZipEntry additionalFileEntry = new ZipEntry(entryKey);
							zipOut.putNextEntry(additionalFileEntry);
							zipOut.write(entry.getValue().getLob());
							zipOut.closeEntry();
						}
					}
				}
			}

			zipOut.finish();
		} catch (final IOException e) {
			throw new InternalIOException(InternalIOException.ZIP_CREATION, "Failed to create the ZIP file!", e);
		}
	}

	private void addCsvToZip(final ZipOutputStream zipOut, final DataSet dataSet, final String name) throws IOException {
		final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(zipOut, StandardCharsets.UTF_8);
		final CSVFormat csvFormat = CSVFormat.Builder.create().setHeader(
				dataSet.getDataConfiguration().getColumnNames().toArray(new String[0])).build();

		final ZipEntry dataZipEntry = new ZipEntry(name + ".csv");
		zipOut.putNextEntry(dataZipEntry);

		final CSVPrinter csvPrinter = new CSVPrinter(outputStreamWriter, csvFormat);
		for (final DataRow dataRow : dataSet.getDataRows()) {
			csvPrinter.printRecord(dataRow.getRow());
		}
		csvPrinter.flush();

		zipOut.closeEntry();
	}
}
