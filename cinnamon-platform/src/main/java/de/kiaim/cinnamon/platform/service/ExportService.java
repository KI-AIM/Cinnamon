package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.model.configuration.ConfigurationDTO;
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
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

	private final YAMLMapper yamlMapper;

	private final DatabaseService databaseService;
	private final DataProcessorService dataProcessorService;
	private final ResourceSelectorService resourceSelectorService;

	public ExportService(
			final YAMLMapper yamlMapper,
			final DatabaseService databaseService,
			final DataProcessorService dataProcessorService,
			final ResourceSelectorService resourceSelectorService
	) {
		this.yamlMapper = yamlMapper;
		this.databaseService = databaseService;
		this.dataProcessorService = dataProcessorService;
		this.resourceSelectorService = resourceSelectorService;
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
	 * Exports the project to a ZIP file and writes it to the given response.
	 *
	 * @param project The project to export.
	 * @param response The response to write the ZIP file to.
	 * @param projectExportParameter Parameter specifying what should be exported.
	 * @return The response entity containing the ZIP file.
	 * @throws BadConfigurationNameException       If the name of a configuration to export is unknown.
	 * @throws BadStateException                   If the project state is invalid for export.
	 * @throws BadStepNameException                If a resource from an unknown step is requested.
	 * @throws InternalDataSetPersistenceException If a dataset could not be exported due to an internal error.
	 * @throws InternalInvalidStateException       If a requested configuration is not valid, i.e., the validation during the import failed.
	 * @throws InternalIOException                 If the dataset could not be serialized.
	 *                                             If adding a resource to the ZIP file failed.
	 * @throws InternalMissingHandlingException    If no data processor for the target file type could be found.
	 */
	@Transactional
	public ResponseEntity<StreamingResponseBody> createZipFile(final ProjectEntity project,
	                                                           final HttpServletResponse response,
	                                                           final ProjectExportParameter projectExportParameter)
			throws BadConfigurationNameException, BadStateException, BadStepNameException,
					       InternalDataSetPersistenceException, InternalInvalidStateException, InternalIOException,
					       InternalMissingHandlingException {

		response.setContentType("application/zip");
		response.setHeader("Content-Disposition",
		                   "attachment; filename=\"" + project.getProjectConfiguration().getProjectName() + "_" +
		                   LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")) + "_Cinnamon.zip\"");

		final OutputStream outputStream;
		try {
			outputStream = response.getOutputStream();
		} catch (final IOException e) {
			throw new InternalIOException(InternalIOException.ZIP_CREATION, "Could not get OutputStream", e);
		}

		createZipFile(project, outputStream, projectExportParameter);

		return ResponseEntity.ok().build();
	}

	/**
	 * Writes a ZIP to the given OutputStream containing the resources specified in the project export parameter.
	 *
	 * @param project                The project to export.
	 * @param outputStream           The OutputStream to write to.
	 * @param projectExportParameter Parameter specifying what should be exported.
	 * @throws BadConfigurationNameException       If the name of a configuration to export is unknown.
	 * @throws BadStateException                   If the project state is invalid for export.
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
			throws BadConfigurationNameException, BadStateException, BadStepNameException,
					       InternalDataSetPersistenceException, InternalInvalidStateException,
					       InternalIOException, InternalMissingHandlingException {
		try (final ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {

			final Map<String, Integer> zipEntryCounter = new HashMap<>();

			List<String> resources = projectExportParameter.getResources();
			if (resources == null || resources.isEmpty()) {
				resources = getAvailableExportResources(project);
			}

			final List<ConfigurationDTO> configurationDTOs = new ArrayList<>();
			for (final String resourceSelector : resources) {
				Object resource = resourceSelectorService.selectResource(resourceSelector, project, null, null);

				if (resource instanceof ConfigurationDTO) {
					configurationDTOs.add((ConfigurationDTO) resource);
				} else if (resource instanceof DataSetEntity dataSetEntity) {
					handleDatasetSelector(projectExportParameter, zipOut, dataSetEntity);
				} else if (resource instanceof FileEntity fileEntity) {
					handleFileSelector(zipOut, fileEntity);
				} else if (resource instanceof ExternalProcessEntity externalProcess) {
					if (externalProcess instanceof DataProcessingEntity dataProcessing) {
						final String name = dataProcessing.getJob().getName() + "-";
						for (final var entry : dataProcessing.getResultFiles().entrySet()) {
							handleLob(zipOut, zipEntryCounter, entry.getValue(), name + entry.getKey());
						}
					}
				} else if (resource instanceof BackgroundProcessEntity statisticsProcess) {
					if (statisticsProcess.getOwner() instanceof DataSetEntity dataSetEntity) {
						if (dataSetEntity.getStatistics() != null) {
							final String name = getDataSetFileName(dataSetEntity);
							handleLob(zipOut, zipEntryCounter, dataSetEntity.getStatistics(),
							          name + "-statistics.yaml");
						}
					}
				}
			}

			if (!configurationDTOs.isEmpty()) {
				addConfigurationsToZip(projectExportParameter, zipOut, configurationDTOs);
			}

			zipOut.finish();
		} catch (final IOException e) {
			throw new InternalIOException(InternalIOException.ZIP_CREATION, "Failed to create the ZIP file!", e);
		}
	}

	/**
	 * Adds the given FileEntity to the ZIP file.
	 *
	 * @param zipOut     The ZIP output stream.
	 * @param fileEntity The FileEntity to add.
	 * @throws IOException If adding a resource to the ZIP file failed.
	 */
	private void handleFileSelector(final ZipOutputStream zipOut, final FileEntity fileEntity)
			throws IOException {
		if (fileEntity != null && fileEntity.getFile() != null) {
			final ZipEntry fileEntry = new ZipEntry("original-file-" + fileEntity.getName());
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
	 * @throws InternalDataSetPersistenceException If the dataset could not be exported due to an internal error.
	 * @throws InternalIOException                 If the dataset could not be serialized.
	 * @throws InternalMissingHandlingException    If no data processor for the target file type could be found.
	 * @throws IOException                         If adding a resource to the ZIP file failed.
	 */
	private void handleDatasetSelector(
			final ProjectExportParameter projectExportParameter,
			final ZipOutputStream zipOut,
			final DataSetEntity dataSetEntity
	) throws InternalDataSetPersistenceException, InternalIOException, InternalMissingHandlingException, IOException {
		if (!dataSetEntity.isStoredData())
			return;

		final String name = getDataSetFileName(dataSetEntity);
		final DataSet dataSet = databaseService.exportDataSet(dataSetEntity, HoldOutSelector.ALL);
		addDatasetToZip(zipOut, dataSet, projectExportParameter.getDatasetFileType(), name + "-dataset");
	}

	private void handleLob(
			final ZipOutputStream zipOut,
			final Map<String, Integer> zipEntryCounter,
			final LobWrapperEntity lobWrapper,
			String name
	) throws IOException {
		if (zipEntryCounter.containsKey(name)) {
			var count = zipEntryCounter.get(name);
			name = name.substring(0, name.lastIndexOf('.')) + "_" + count +
			           name.substring(name.lastIndexOf('.'));
			zipEntryCounter.put(name, count + 1);
		} else {
			zipEntryCounter.put(name, 1);
		}

		final ZipEntry additionalFileEntry = new ZipEntry(name);
		zipOut.putNextEntry(additionalFileEntry);
		zipOut.write(lobWrapper.getLob());
		zipOut.closeEntry();
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
	 * @param projectExportParameter The parameter specifying what should be exported.
	 * @param zipOut                 The ZIP output stream.
	 * @throws IOException If adding a configuration to the ZIP file failed.
	 */
	private void addConfigurationsToZip(
			final ProjectExportParameter projectExportParameter,
			final ZipOutputStream zipOut,
			final List<ConfigurationDTO> configurationDTOs
	) throws IOException {

		if (projectExportParameter.isBundleConfigurations()) {
			final StringBuilder bundledConfigurations = new StringBuilder();

			for (final ConfigurationDTO config : configurationDTOs) {
				final String configurationString = getConfigurationString(config);
				bundledConfigurations.append(configurationString);
			}

			final ZipEntry configZipEntry = new ZipEntry("all-configurations.yaml");
			zipOut.putNextEntry(configZipEntry);
			zipOut.write(bundledConfigurations.toString().getBytes());
			zipOut.closeEntry();

		} else {
			// Add configurations
			for (final ConfigurationDTO config : configurationDTOs) {
				final String configString = getConfigurationString(config);

				final ZipEntry configZipEntry = new ZipEntry(config.getKey() + ".yaml");
				zipOut.putNextEntry(configZipEntry);
				zipOut.write(configString.getBytes());
				zipOut.closeEntry();
			}
		}
	}

	/**
	 * Gets the configuration string for the given configuration.
	 * Wraps the configuration in a parent if it does not include the key already.
	 *
	 * @param config The configuration to export.
	 * @return The configuration string.
	 */
	private String getConfigurationString(final ConfigurationDTO config){
		if (!config.includesKey()) {
			// Wrap the configuration in a parent
			final Map<String, Object> parentMap = new HashMap<>();
			parentMap.put(config.getKey(), config);
			return yamlMapper.writeValueAsString(parentMap);
		}

		return yamlMapper.writeValueAsString(config);
	}

	private String getDataSetFileName(final DataSetEntity dataSetEntity) {
		return !dataSetEntity.getProcessed().isEmpty()
		       ? dataSetEntity.getProcessed().stream().map(Job::getName)
		                      .collect(Collectors.joining("-"))
		       : "original";
	}

}
