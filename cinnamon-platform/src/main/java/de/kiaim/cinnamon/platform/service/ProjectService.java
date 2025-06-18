package de.kiaim.cinnamon.platform.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.data.DataRow;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.configuration.CinnamonConfiguration;
import de.kiaim.cinnamon.platform.model.configuration.ExternalConfiguration;
import de.kiaim.cinnamon.platform.model.configuration.Stage;
import de.kiaim.cinnamon.platform.model.configuration.Job;
import de.kiaim.cinnamon.platform.model.dto.ProjectExportParameter;
import de.kiaim.cinnamon.platform.model.entity.*;
import de.kiaim.cinnamon.platform.model.enumeration.HoldOutSelector;
import de.kiaim.cinnamon.platform.model.enumeration.Mode;
import de.kiaim.cinnamon.platform.model.enumeration.Step;
import de.kiaim.cinnamon.platform.repository.ProjectRepository;
import de.kiaim.cinnamon.platform.repository.UserRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.lang.Nullable;
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

	@Transactional
	public void deleteProject(final UserEntity user) throws InternalDataSetPersistenceException, BadDataSetIdException {
		if (hasProject(user)) {
			final ProjectEntity p = getProject(user);
			databaseService.delete(p);
			projectRepository.deleteById(p.getId());
		}
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
	 * Writes a ZIP to the given OutputStream containing the data set and data configuration of the given project and the given configuration.
	 *
	 * @param project                The project of the data set.
	 * @param outputStream           The OutputStream to write to.
	 * @param projectExportParameter Parameter specifying what should be exported.
	 * @throws InternalDataSetPersistenceException If the data set could not be exported due to an internal error.
	 * @throws InternalIOException                 If the request body could not be created.
	 */
	@Transactional
	public void createZipFile(final ProjectEntity project, final OutputStream outputStream,
	                          final ProjectExportParameter projectExportParameter)
			throws InternalDataSetPersistenceException, InternalIOException, BadConfigurationNameException, BadStepNameException {
		try (final ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {

			if (projectExportParameter.isBundleConfigurations()) {
				StringBuilder bundledConfigurations = new StringBuilder();

				for (final String configName : projectExportParameter.getConfigurationNames()) {
					final String configurationString = getConfigurationString(project, configName);
					if (configurationString != null) {
						bundledConfigurations.append(configurationString);
					}
				}

				final ZipEntry configZipEntry = new ZipEntry("configurations.yaml");
				zipOut.putNextEntry(configZipEntry);
				zipOut.write(bundledConfigurations.toString().getBytes());
				zipOut.closeEntry();

			} else {
				// Add configurations
				for (final String configName : projectExportParameter.getConfigurationNames()) {
					// Special case for data configuration
					if (configName.equals("configurations")) {

						final DataSetEntity dataSetEntity = project.getOriginalData().getDataSet();
						if (dataSetEntity != null) {
							final ZipEntry attributeConfigZipEntry = new ZipEntry("attribute_config.yaml");
							zipOut.putNextEntry(attributeConfigZipEntry);
							yamlMapper.writer().without(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
							          .writeValue(zipOut, dataSetEntity.getDataConfiguration());
							zipOut.closeEntry();
						}
					} else {
						final ExternalConfiguration externalConfiguration = stepService.getExternalConfiguration(
								configName);
						final ConfigurationListEntity configList = project.getConfigurationList(externalConfiguration);

						if (configList == null) {
							continue;
						}

						final String config = configList.getConfigurations().get(0).getConfiguration();

						if (config == null) {
							continue;
						}

						final ZipEntry configZipEntry = new ZipEntry(configName + ".yaml");
						zipOut.putNextEntry(configZipEntry);
						zipOut.write(config.getBytes());
						zipOut.closeEntry();
					}
				}
			}

			Map<String, Integer> zipEntryCounter = new HashMap<>();

			final PipelineEntity pipeline = project.getPipelines().get(0);
			for (final String resultSelector : projectExportParameter.getResults()) {
				final String[] parts = resultSelector.split("\\.");

				if (parts[0].equals("original")) {

					final DataSetEntity dataSetEntity = project.getOriginalData().getDataSet();
					if (dataSetEntity != null) {

						if (parts[1].equals("dataset")) {

							if (dataSetEntity.isStoredData()) {
								final DataSet dataSet = databaseService.exportDataSet(dataSetEntity,
								                                                      HoldOutSelector.ALL);
								addCsvToZip(zipOut, dataSet, "original");
							}

						} else if (parts[1].equals("statistics")) {

							final LobWrapperEntity statistics = project.getOriginalData().getDataSet().getStatistics();
							if (statistics != null) {
								final ZipEntry statisticsEntry = new ZipEntry("statistics.yaml");
								zipOut.putNextEntry(statisticsEntry);
								zipOut.write(statistics.getLob());
								zipOut.closeEntry();
							}
						}
					}

				} else if (parts[0].equals("pipeline")) {

					final Stage stage = stepService.getStageConfiguration(parts[1]);
					final ExecutionStepEntity executionStep = pipeline.getStageByStep(stage);

					final Job job = stepService.getStepConfiguration(parts[2]);
					final ExternalProcessEntity externalProcess = executionStep.getProcess(job).get();

					if (externalProcess instanceof DataProcessingEntity dataProcessing) {
						if (dataProcessing.getDataSet() != null) {
							final String name = dataProcessing.getDataSet().getProcessed().stream().map(Job::getName)
							                                  .collect(Collectors.joining("-"));

							if (parts[3].equals("dataset")) {

								if (dataProcessing.getDataSet().isStoredData()) {
									addCsvToZip(zipOut, databaseService.exportDataSet(dataProcessing.getDataSet(),
									                                                  HoldOutSelector.ALL), name);
								}

							} else if (parts[3].equals("statistics")) {
								final var resultFiles = dataProcessing.getDataSet().getStatisticsProcess()
								                                      .getResultFiles();

								// TODO calculate statistics if not present?
								if (resultFiles.containsKey("metrics.json")) {
									final var statisticsLob = resultFiles.get("metrics.json");
									final ZipEntry configZipEntry = new ZipEntry(name + "-statistics.json");
									zipOut.putNextEntry(configZipEntry);
									zipOut.write(statisticsLob.getLob());
									zipOut.closeEntry();
								}
							}
						}
					}

					if (parts[3].equals("other")) {
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

	private void addCsvToZip(final ZipOutputStream zipOut, final DataSet dataSet,
	                         final String name) throws IOException {
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

	@Nullable
	private String getConfigurationString(final ProjectEntity project,
	                                      final String configName) throws JsonProcessingException, BadConfigurationNameException {
		// Special case for data configurations
		if (configName.equals("configurations")) {
			final DataSetEntity dataSetEntity = project.getOriginalData().getDataSet();

			if (dataSetEntity == null) {
				return null;
			}
			return yamlMapper.writeValueAsString(dataSetEntity.getDataConfiguration());

		} else {
			final ExternalConfiguration externalConfiguration = stepService.getExternalConfiguration(configName);
			final ConfigurationListEntity configList = project.getConfigurationList(externalConfiguration);

			if (configList == null) {
				return null;
			}

			return configList.getConfigurations().get(0).getConfiguration();
		}
	}

}
