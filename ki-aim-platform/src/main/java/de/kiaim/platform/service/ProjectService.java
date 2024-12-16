package de.kiaim.platform.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.model.data.DataRow;
import de.kiaim.model.data.DataSet;
import de.kiaim.platform.model.configuration.KiAimConfiguration;
import de.kiaim.platform.model.configuration.StageConfiguration;
import de.kiaim.platform.model.configuration.StepConfiguration;
import de.kiaim.platform.exception.InternalApplicationConfigurationException;
import de.kiaim.platform.exception.InternalDataSetPersistenceException;
import de.kiaim.platform.exception.InternalIOException;
import de.kiaim.platform.model.entity.*;
import de.kiaim.platform.model.enumeration.Mode;
import de.kiaim.platform.model.enumeration.Step;
import de.kiaim.platform.repository.ProjectRepository;
import de.kiaim.platform.repository.UserRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service class for managing projects.
 */
@Service
public class ProjectService {

	private final ObjectMapper yamlMapper;
	private final KiAimConfiguration kiAimConfiguration;
	private final ProjectRepository projectRepository;
	private final UserRepository userRepository;
	private final DatabaseService databaseService;
	private final StepService stepService;

	public ProjectService(final ObjectMapper yamlMapper, final KiAimConfiguration kiAimConfiguration,
	                      final ProjectRepository projectRepository, final UserRepository userRepository,
	                      final DatabaseService databaseService,
	                      final StepService stepService) {
		this.yamlMapper = yamlMapper;
		this.kiAimConfiguration = kiAimConfiguration;
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
	 * @param user The user.
	 * @return The projects of the user.
	 * @throws InternalApplicationConfigurationException If a referenced step is not configured.
	 */
	@Transactional
	public ProjectEntity createProject(final UserEntity user) throws InternalApplicationConfigurationException {
		if (hasProject(user)) {
			return user.getProject();
		}

		final ProjectEntity project = createProject();
		user.setProject(project);

		userRepository.save(user);

		return project;
	}

	/**
	 * Creates a new empty project that is not associated to any user.
	 *
	 * @return The project.
	 * @throws InternalApplicationConfigurationException If a referenced step is not configured.
	 */
	public ProjectEntity createProject() throws InternalApplicationConfigurationException {
		final ProjectEntity project = new ProjectEntity();

		final PipelineEntity pipeline = new PipelineEntity();
		project.addPipeline(pipeline);

		// Create entities for external processes
		for (final Step stageStep : kiAimConfiguration.getPipeline().getStages()) {
			if (!kiAimConfiguration.getStages().containsKey(stageStep)) {
				throw new InternalApplicationConfigurationException(
						InternalApplicationConfigurationException.MISSING_STAGE_CONFIGURATION,
						"No configuration for stage '" + stageStep + "'!");
			}

			final StageConfiguration stageConfiguration = kiAimConfiguration.getStages().get(stageStep);
			final ExecutionStepEntity stage = new ExecutionStepEntity();

			for (final Step jobStep : stageConfiguration.getJobs()) {
				if (!kiAimConfiguration.getSteps().containsKey(jobStep)) {
					throw new InternalApplicationConfigurationException(
							InternalApplicationConfigurationException.MISSING_STEP_CONFIGURATION,
							"No configuration for step '" + jobStep + "'!");
				}

				final StepConfiguration stepConfiguration = kiAimConfiguration.getSteps().get(jobStep);
				ExternalProcessEntity job = switch (stepConfiguration.getStepType()) {
					case DATA_PROCESSING -> new DataProcessingEntity();
					case EVALUATION -> new EvaluationProcessingEntity();
				};

				job.setEndpoint(stepConfiguration.getExternalServerEndpointIndex());
				job.setStep(jobStep);
				stage.addProcess(job);
			}

			pipeline.addStage(stageStep, stage);
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
		project.getStatus().setCurrentStep(Step.UPLOAD);
		userRepository.save(project.getUser());
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
			final DataSet dataSet = databaseService.exportDataSet(project.getOriginalData().getDataSet());

			// Add data configuration
			final ZipEntry attributeConfigZipEntry = new ZipEntry("attribute_config.yaml");
			zipOut.putNextEntry(attributeConfigZipEntry);
			yamlMapper.writer().without(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
			          .writeValue(zipOut, dataSet.getDataConfiguration());
			zipOut.closeEntry();

			// Add data set
			addCsvToZip(zipOut, dataSet, "original");

			// Add statistics
			if (project.getOriginalData().getStatistics() != null) {
				final ZipEntry statisticsEntry = new ZipEntry("statistics.yaml");
				zipOut.putNextEntry(statisticsEntry);
				zipOut.write(project.getOriginalData().getStatistics().getLob());
				zipOut.closeEntry();
			}

			// Add results
			for (final PipelineEntity pipeline : project.getPipelines()) {
				for (final ExecutionStepEntity executionStep : pipeline.getStages()) {
					for (final ExternalProcessEntity externalProcess : executionStep.getProcesses()) {
						// Add configuration
						if (externalProcess.getConfiguration() != null) {
							final String configurationName = stepService.getExternalServerEndpointConfiguration(externalProcess.getStep())
							                                            .getConfigurationName();
							final ZipEntry configZipEntry = new ZipEntry(configurationName + ".yaml");
							zipOut.putNextEntry(configZipEntry);
							zipOut.write(externalProcess.getConfiguration().getBytes());
							zipOut.closeEntry();
						}

						// Add data set
						if (externalProcess instanceof DataProcessingEntity dataProcessing ) {
							if (dataProcessing.getDataSet() != null && dataProcessing.getDataSet().isStoredData()) {
								addCsvToZip(zipOut, databaseService.exportDataSet(dataProcessing.getDataSet()),
								            dataProcessing.getDataSet().getProcessed().stream().map(Step::name)
								                          .collect(Collectors.joining("-")));
							}
						}

						// Add additional files
						for (final var entry : externalProcess.getResultFiles().entrySet()) {
							final ZipEntry additionalFileEntry = new ZipEntry(entry.getKey());
							zipOut.putNextEntry(additionalFileEntry);
							zipOut.write(entry.getValue().getLob());
							zipOut.closeEntry();
						}
					}
				}
			}

			zipOut.finish();
		} catch (final IOException | InternalApplicationConfigurationException e) {
			throw new InternalIOException(InternalIOException.ZIP_CREATION,
			                              "Failed to create the ZIP file for starting an external process!", e);
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
