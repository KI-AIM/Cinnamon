package de.kiaim.cinnamon.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.configuration.ConfigurationFile;
import de.kiaim.cinnamon.model.data.DataSet;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.configuration.Job;
import de.kiaim.cinnamon.platform.model.configuration.Stage;
import de.kiaim.cinnamon.platform.model.dto.ProjectExportParameter;
import de.kiaim.cinnamon.platform.model.entity.*;
import de.kiaim.cinnamon.platform.model.enumeration.HoldOutSelector;
import de.kiaim.cinnamon.model.configuration.data.file.FileType;
import de.kiaim.cinnamon.platform.processor.DataProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service for exporting projects.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class ExportService {

	private final ObjectMapper yamlMapper;

	private final ConfigurationService configurationService;
	private final DatabaseService databaseService;
	private final DataProcessorService dataProcessorService;
	private final StepService stepService;

	public ExportService(
			final ObjectMapper yamlMapper,
			final ConfigurationService configurationService,
			final DatabaseService databaseService,
			final DataProcessorService dataProcessorService,
			final StepService stepService
	) {
		this.yamlMapper = yamlMapper;
		this.configurationService = configurationService;
		this.databaseService = databaseService;
		this.dataProcessorService = dataProcessorService;
		this.stepService = stepService;
	}

	/**
	 * Returns a list of available export resources for the given project.
	 *
	 * @param project The project entity for which to retrieve available export resources.
	 * @return A list of resource identifiers that can be exported.
	 */
	public List<String> getAvailableExportResources(final ProjectEntity project) {
		final List<String> resources = new ArrayList<>();

		// Add platform configurations that are always available
		resources.add("configuration." + ConfigurationFile.PIPELINE_CONFIGURATION_KEY);
		resources.add("configuration." + ConfigurationFile.PROJECT_CONFIGURATION_KEY);
		resources.add("configuration." + ConfigurationFile.DATASET_CONFIGURATION_KEY);

		// Add file-related configurations/ data
		if (project.getOriginalData().getFile() != null) {
			if (project.getOriginalData().getFile().getFileConfiguration() != null) {
				resources.add("configuration." + ConfigurationFile.DATA_SOURCE_CONFIGURATION_KEY);
			}
			if (project.getOriginalData().getFile().getFile() != null) {
				resources.add("original.file");
			}
		}

		// Add original dataset-related configurations/ data
		if (project.getOriginalData().getDataSet() != null) {
			if (project.getOriginalData().getDataSet().getDataConfiguration() != null) {
				resources.add("configuration." + ConfigurationFile.DATA_CONFIGURATION_KEY);
			}
			if (project.getOriginalData().getDataSet().isStoredData()) {
				resources.add("original.dataset");
			}
			if (project.getOriginalData().getDataSet().getStatistics() != null) {
				resources.add("original.statistics");
			}
		}

		// Add module configurations
		resources.addAll(project.getConfigurations().stream()
		                        .map(config-> "configuration." + config.getConfiguration().getConfigurationName())
		                        .toList());

		// Add results
		if (!project.getPipelines().isEmpty()) {
			final PipelineEntity pipeline = project.getPipelines().get(0);

			for (final ExecutionStepEntity executionStep : pipeline.getStages()) {
				final Stage stage = executionStep.getStage();

				for (final ExternalProcessEntity process : executionStep.getProcesses()) {
					final Job job = process.getJob();

					if (process instanceof DataProcessingEntity dataProcessing) {
						if (dataProcessing.getDataSet() != null) {
							resources.add("pipeline." + stage.getStageName() + "." + job.getName() + ".dataset");

							if (dataProcessing.getDataSet().getStatistics() != null) {
								resources.add("pipeline." + stage.getStageName() + "." + job.getName() + ".statistics");
							}
						}
					}

					if (!process.getResultFiles().isEmpty()) {
						resources.add("pipeline." + stage.getStageName() + "." + job.getName() + ".other");
					}
				}
			}
		}

		return resources;
	}

	/**
	 * Writes a ZIP to the given OutputStream containing the resources specified in the project export parameter.
	 *
	 * @param project                The project to export.
	 * @param outputStream           The OutputStream to write to.
	 * @param projectExportParameter Parameter specifying what should be exported.
	 * @throws BadConfigurationNameException       If the name of a configuration to export is unknown.
	 * @throws BadStepNameException                If a resource from an unknown step is requested.
	 * @throws InternalDataSetPersistenceException If a dataset could not be exported due to an internal error.
	 * @throws InternalInvalidStateException       If a requested configuration is not valid, i.e., the validation during the import failed.
	 * @throws InternalIOException                 If the dataset could not be serialized.
	 *                                             If adding a resource to the ZIP file failed.
	 * @throws InternalMissingHandlingException    If no data processor for the target file type could be found.
	 */
	@Transactional
	public void createZipFile(final ProjectEntity project, final OutputStream outputStream,
	                          final ProjectExportParameter projectExportParameter)
			throws BadConfigurationNameException, BadStateException, BadStepNameException, InternalDataSetPersistenceException, InternalInvalidStateException, InternalIOException, InternalMissingHandlingException {
		try (final ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {

			final Map<String, Integer> zipEntryCounter = new HashMap<>();

			List<String> resources = projectExportParameter.getResources();
			if (resources == null || resources.isEmpty()) {
				resources = getAvailableExportResources(project);
			}

			final List<String> configurationNames = new ArrayList<>();
			for (final String resource : resources) {
				final String[] parts = resource.split("\\.");

				switch (parts[0]) {
					case "configuration" -> configurationNames.add(parts[1]);
					case "original" -> handleOriginalSelector(project, projectExportParameter, zipOut, parts);
					case "pipeline" -> handlePipelineSelector(project, projectExportParameter, zipOut, parts, zipEntryCounter);
				}
			}

			if (!configurationNames.isEmpty()) {
				addConfigurationsToZip(project, projectExportParameter, zipOut, configurationNames);
			}

			zipOut.finish();
		} catch (final IOException e) {
			throw new InternalIOException(InternalIOException.ZIP_CREATION, "Failed to create the ZIP file!", e);
		}
	}

	/**
	 * Adds resources from the original dataset to the ZIP file.
	 *
	 * @param project                The project to export.
	 * @param projectExportParameter The parameter specifying what should be exported.
	 * @param zipOut                 The ZIP output stream.
	 * @param parts                  The parts of the resource name.
	 * @throws InternalDataSetPersistenceException If the dataset could not be exported due to an internal error.
	 * @throws InternalIOException                 If the dataset could not be serialized.
	 * @throws InternalMissingHandlingException    If no data processor for the target file type could be found.
	 * @throws IOException                         If adding a resource to the ZIP file failed.
	 */
	private void handleOriginalSelector(
			final ProjectEntity project,
			final ProjectExportParameter projectExportParameter,
			final ZipOutputStream zipOut,
			final String[] parts
	) throws InternalDataSetPersistenceException, InternalIOException, InternalMissingHandlingException, IOException {
		final DataSetEntity dataSetEntity = project.getOriginalData().getDataSet();
		if (dataSetEntity != null) {
			switch (parts[1]) {
				case "file" -> handleFileSelector(zipOut, project.getOriginalData().getFile(), "original");
				case "dataset" -> handleDatasetSelector(projectExportParameter, zipOut, dataSetEntity, "original");
				case "statistics" -> handleStatisticsSelector(zipOut, dataSetEntity.getStatistics(), "original");
			}
		}
	}

	/**
	 * Adds resources from the pipeline to the ZIP file.
	 *
	 * @param project                The project to export.
	 * @param projectExportParameter The parameter specifying what should be exported.
	 * @param zipOut                 The ZIP output stream.
	 * @param parts                  The parts of the resource name.
	 * @param zipEntryCounter        Counter for ZIP entry names.
	 * @throws BadStepNameException                If the step name defined in the parts is invalid.
	 * @throws InternalDataSetPersistenceException If the dataset could not be exported due to an internal error.
	 * @throws InternalInvalidStateException       If the project state is invalid for export.
	 * @throws InternalIOException                 If the dataset could not be serialized.
	 * @throws InternalMissingHandlingException    If no data processor for the target file type could be found.
	 * @throws IOException                         If adding a resource to the ZIP file failed.
	 */
	private void handlePipelineSelector(
			final ProjectEntity project,
			final ProjectExportParameter projectExportParameter,
			final ZipOutputStream zipOut,
			final String[] parts,
			final Map<String, Integer> zipEntryCounter
	) throws BadStepNameException, InternalDataSetPersistenceException, InternalInvalidStateException, InternalIOException, InternalMissingHandlingException, IOException {
		final PipelineEntity pipeline = project.getPipelines().get(0);
		final Stage stage = stepService.getStageConfiguration(parts[1]);
		final ExecutionStepEntity executionStep = pipeline.getStageByStep(stage);

		if (executionStep == null) {
			throw new InternalInvalidStateException(InternalInvalidStateException.MISSING_STAGE,
			                                        "Execution step not found for stage: " + stage.getStageName());
		}

		final Job job = stepService.getStepConfiguration(parts[2]);
		final ExternalProcessEntity externalProcess = executionStep.getProcess(job).orElseThrow(
				() -> new InternalInvalidStateException(InternalInvalidStateException.MISSING_PROCESS_ENTITY,
				                                        "External process not found for job: " + job.getName()));

		if (externalProcess instanceof DataProcessingEntity dataProcessing) {
			if (dataProcessing.getDataSet() != null) {
				final String name = dataProcessing.getDataSet().getProcessed().stream().map(Job::getName)
				                                  .collect(Collectors.joining("-"));

				switch (parts[3]) {
					case "dataset" ->
							handleDatasetSelector(projectExportParameter, zipOut, dataProcessing.getDataSet(), name);
					case "statistics" ->
							handleStatisticsSelector(zipOut, dataProcessing.getDataSet().getStatisticsProcess()
							                                               .getResultFiles()
							                                               .getOrDefault("metrics.json", null),
							                         name);
				}
			}
		}

		if (parts[3].equals("other")) {
			for (final var entry : externalProcess.getResultFiles().entrySet()) {
				String entryKey = job.getName() + "-" + entry.getKey();
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

	/**
	 * Adds the given FileEntity to the ZIP file.
	 *
	 * @param zipOut     The ZIP output stream.
	 * @param fileEntity The FileEntity to add.
	 * @param name       The name of the source step.
	 * @throws IOException If adding a resource to the ZIP file failed.
	 */
	private void handleFileSelector(final ZipOutputStream zipOut, final FileEntity fileEntity, final String name)
			throws IOException {
		if (fileEntity != null && fileEntity.getFile() != null) {
			final ZipEntry fileEntry = new ZipEntry(name + "-file-" + fileEntity.getName());
			zipOut.putNextEntry(fileEntry);
			zipOut.write(fileEntity.getFile().getLob());
			zipOut.closeEntry();
		}
	}

	/**
	 * Adds the given dataset to the ZIP file.
	 *
	 * @param projectExportParameter The parameter specifying what should be exported.
	 * @param zipOut                 The ZIP output stream.
	 * @param dataSetEntity          The dataset to add.
	 * @param name                   The name of the dataset.
	 * @throws InternalDataSetPersistenceException If the dataset could not be exported due to an internal error.
	 * @throws InternalIOException                 If the dataset could not be serialized.
	 * @throws InternalMissingHandlingException    If no data processor for the target file type could be found.
	 * @throws IOException                         If adding a resource to the ZIP file failed.
	 */
	private void handleDatasetSelector(
			final ProjectExportParameter projectExportParameter,
			final ZipOutputStream zipOut,
			final DataSetEntity dataSetEntity,
			final String name
	) throws InternalDataSetPersistenceException, InternalIOException, InternalMissingHandlingException, IOException {
		if (dataSetEntity.isStoredData()) {
			final DataSet dataSet = databaseService.exportDataSet(dataSetEntity,
			                                                      HoldOutSelector.ALL);
			addDatasetToZip(zipOut, dataSet, projectExportParameter.getDatasetFileType(), name + "-dataset");
		}
	}

	/**
	 * Adds the given LOB resource to the ZIP file.
	 *
	 * @param zipOut The ZIP output stream.
	 * @param name   The name of the source step.
	 * @throws IOException If adding a resource to the ZIP file failed.
	 */
	private void handleStatisticsSelector(
			final ZipOutputStream zipOut,
			final LobWrapperEntity statistics,
			final String name
	) throws IOException {
		if (statistics != null) {
			final ZipEntry statisticsEntry = new ZipEntry(name + "-statistics.yaml");
			zipOut.putNextEntry(statisticsEntry);
			zipOut.write(statistics.getLob());
			zipOut.closeEntry();
		}
	}

	/**
	 * Adds the given dataset to the ZIP file.
	 *
	 * @param zipOut   The ZIP output stream.
	 * @param dataSet  The dataset to add.
	 * @param fileType The target file type of the dataset.
	 * @param name     The name of the dataset.
	 * @throws InternalIOException              If the dataset could not be serialized.
	 * @throws InternalMissingHandlingException If no data processor for the target file type could be found.
	 * @throws IOException                      If adding a resource to the ZIP file failed.
	 */
	private void addDatasetToZip(final ZipOutputStream zipOut, final DataSet dataSet, final FileType fileType,
	                             final String name)
			throws InternalIOException, InternalMissingHandlingException, IOException {

		final String fileExtension = fileType.getFileExtensions().iterator().next();
		final ZipEntry dataZipEntry = new ZipEntry(name + fileExtension);
		zipOut.putNextEntry(dataZipEntry);

		final DataProcessor dataProcessor = dataProcessorService.getDataProcessor(fileType);
		dataProcessor.write(zipOut, dataSet);

		zipOut.closeEntry();
	}

	/**
	 * Adds the configurations with the given names to the ZIP file.
	 *
	 * @param project                The project to export the configurations for.
	 * @param projectExportParameter The parameter specifying what should be exported.
	 * @param zipOut                 The ZIP output stream.
	 * @param configurationNames     The names of the configurations to add.
	 * @throws BadConfigurationNameException If a configuration name is invalid.
	 * @throws BadStateException             If the project state is invalid for export.
	 * @throws InternalIOException           If the configuration could not be serialized.
	 * @throws InternalInvalidStateException If the configuration is not valid.
	 * @throws IOException                   If adding a configuration to the ZIP file failed.
	 */
	private void addConfigurationsToZip(
			final ProjectEntity project,
			final ProjectExportParameter projectExportParameter,
			final ZipOutputStream zipOut,
			final List<String> configurationNames
	) throws BadConfigurationNameException, BadStateException, InternalIOException, InternalInvalidStateException, IOException {

		if (projectExportParameter.isBundleConfigurations()) {
			final StringBuilder bundledConfigurations = new StringBuilder();

			for (final String configName : configurationNames) {
				final String configurationString = getConfigurationString(project, configName);
				bundledConfigurations.append(configurationString);
			}

			final ZipEntry configZipEntry = new ZipEntry("all-configurations.yaml");
			zipOut.putNextEntry(configZipEntry);
			zipOut.write(bundledConfigurations.toString().getBytes());
			zipOut.closeEntry();

		} else {
			// Add configurations
			for (final String configName : configurationNames) {
				final String config = getConfigurationString(project, configName);

				final ZipEntry configZipEntry = new ZipEntry(configName + ".yaml");
				zipOut.putNextEntry(configZipEntry);
				zipOut.write(config.getBytes());
				zipOut.closeEntry();
			}
		}
	}

	/**
	 * Gets the configuration string for the given configuration name.
	 * Wraps the configuration in a parent if it is not a configuration for external modules.
	 *
	 * @param project The project to export the configuration for.
	 * @param configName The name of the configuration to export.
	 * @return The configuration string.
	 * @throws JsonProcessingException If the configuration could not be serialized.
	 * @throws BadConfigurationNameException If the project does not have a configuration with the given name.
	 * @throws BadStateException             If the data configuration does not exist.
	 * @throws InternalIOException           If the DataConfiguration could not be deserialized from the stored JSON.
	 * @throws InternalInvalidStateException If the configuration is not valid.
	 */
	private String getConfigurationString(
			final ProjectEntity project,
			final String configName
	) throws JsonProcessingException, BadConfigurationNameException, BadStateException, InternalIOException, InternalInvalidStateException {
		Object config = configurationService.loadConfiguration(configName, project);
		if (!stepService.isExternalConfiguration(configName) &&
		    !configName.equals(ConfigurationFile.DATA_CONFIGURATION_KEY)) {
			// Wrap the configuration in a parent
			final Map<String, Object> parentMap = new HashMap<>();
			parentMap.put(configName, config);
			config = parentMap;
		}

		return yamlMapper.writeValueAsString(config);
	}

}
