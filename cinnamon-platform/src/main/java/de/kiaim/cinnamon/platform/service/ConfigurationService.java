package de.kiaim.cinnamon.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.configuration.ConfigurationDTO;
import de.kiaim.cinnamon.model.configuration.ConfigurationFile;
import de.kiaim.cinnamon.model.configuration.ConfigurationPart;
import de.kiaim.cinnamon.model.configuration.ExternalConfigurationWrapper;
import de.kiaim.cinnamon.model.configuration.algorithms.AlgorithmSelector;
import de.kiaim.cinnamon.model.configuration.data.DataSourceConfiguration;
import de.kiaim.cinnamon.model.configuration.data.DatasetConfiguration;
import de.kiaim.cinnamon.model.configuration.data.attributes.DataConfiguration;
import de.kiaim.cinnamon.model.configuration.data.file.FileConfiguration;
import de.kiaim.cinnamon.model.configuration.pipeline.PipelinesConfigurationDTO;
import de.kiaim.cinnamon.model.configuration.project.ProjectConfigurationDTO;
import de.kiaim.cinnamon.model.dto.ConfigurationImportParameters;
import de.kiaim.cinnamon.model.dto.ConfigurationImportSummary;
import de.kiaim.cinnamon.model.dto.ErrorDetails;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import de.kiaim.cinnamon.platform.controller.ApiExceptionHandler;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.entity.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service class for accessing and managing configurations.
 * Implements unified handling for external configurations, the data configuration, and possible future special configurations.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class ConfigurationService {

	/**
	 * Import order for configurations where the order matters.
	 * Configurations not specified in the import order will be imported before the ones specified in the import order.
	 */
	private final static List<String> CONFIGURATION_IMPORT_ORDER = List.of(ConfigurationFile.PIPELINE_CONFIGURATION_KEY);

	private final ObjectMapper yamlMapper;

	private final Validator validator;

	private final DatabaseService databaseService;
	private final ProcessService processService;
	private final ProjectService projectService;
	private final StepService stepService;

	public ConfigurationService(
			final SerializationConfig serializationConfig,
			final Validator validator,
			final DatabaseService databaseService,
			final ProcessService processService,
			final ProjectService projectService,
			final StepService stepService
	) {
		this.yamlMapper = serializationConfig.yamlMapper();
		this.validator = validator;
		this.databaseService = databaseService;
		this.processService = processService;
		this.projectService = projectService;
		this.stepService = stepService;
	}

	/**
	 * Imports a configuration file into the project.
	 * The root object of the YAML must be an object with its keys being the configuration names as defined in the cinnamon configuration.
	 * Invalid configuration names that are not selected for import will not cause errors.
	 * Configurations of external modules for older versions are updated to be compatible with the current version.
	 * The content of the data configuration and configurations for external modules is not validated.
	 *
	 * @param project    The project the configurations are imported to.
	 * @param file       The configuration file.
	 * @param parameters Parameters for the import.
	 * @return The summary of the imported configurations.
	 * @throws BadConfigurationFileException If the file is not a valid YAML file.
	 */
	@Transactional(rollbackFor = {BadConfigurationFileException.class})
	public ConfigurationImportSummary importConfigurations(
			final ProjectEntity project,
			final MultipartFile file,
			final ConfigurationImportParameters parameters
	) throws BadConfigurationFileException {
		if (file == null) {
			throw new BadConfigurationFileException(BadConfigurationFileException.MISSING,
			                                        "The configuration file is missing");
		}
		if (file.isEmpty()) {
			throw new BadConfigurationFileException(BadConfigurationFileException.EMPTY,
			                                        "The configuration file is empty");
		}

		final JsonNode yamlConfig;
		try {
			yamlConfig = yamlMapper.readTree(file.getInputStream());
		} catch (final JsonProcessingException e) {
			throw new BadConfigurationFileException(BadConfigurationFileException.INVALID_YAML,
			                                        "Invalid YAML file format", e);
		} catch (final IOException e) {
			throw new BadConfigurationFileException(BadConfigurationFileException.NOT_READABLE,
			                                        "File could not be read", e);
		}

		return importConfigurations(project, yamlConfig, parameters);
	}

	/**
	 * Imports a single configuration for an external module.
	 * Configurations of older versions are updated to be compatible with the current version.
	 *
	 * @param project    The project the configuration is imported to.
	 * @param config     The configuration string.
	 * @throws BadAlgorithmException         If no algorithm is specified in the configuration.
	 * @throws BadConfigurationNameException If the configuration name is not valid.
	 * @throws BadStateException             If the process is running or scheduled.
	 * @throws InternalIOException           If parsing the JsonNode failed.
	 */
	public void importExternalConfiguration(final ProjectEntity project, final String config)
			throws BadAlgorithmException, BadConfigurationFileException, BadConfigurationNameException, BadStateException, InternalIOException {
		final ConfigurationPart part;
		final String configurationName;
		try {
			final var a = yamlMapper.readValue(config, ConfigurationFile.class);
			var entry = a.getParts().entrySet().stream().findFirst();

			if (entry.isEmpty()) {
				throw new BadConfigurationFileException(BadConfigurationFileException.INVALID_YAML,
				                                        "No configuration parts found in the file!");
			}

			configurationName = entry.get().getKey();
			part = entry.get().getValue();
		} catch (final JsonProcessingException e) {
			throw new BadConfigurationFileException(BadConfigurationFileException.INVALID_YAML,
			                                        "Failed to deserialize the configuration!",
			                                        e);
		}

		importExternalConfiguration(project, part, configurationName);
	}

	/**
	 * See {@link #importConfigurations(ProjectEntity, MultipartFile, ConfigurationImportParameters)}
	 */
	private ConfigurationImportSummary importConfigurations(
			final ProjectEntity project,
			final JsonNode yamlConfig,
			final ConfigurationImportParameters parameters
	) throws BadConfigurationFileException {
		if (!yamlConfig.isObject()) {
			throw new BadConfigurationFileException(BadConfigurationFileException.ROOT_NOT_OBJECT,
			                                        "The root of the configuration file must be an object!");
		}

		final var importSummary = new ConfigurationImportSummary(parameters);

		final var seenConfigs = new HashSet<String>();

		// Extract the configuration names
		final List<String> fieldNames = new ArrayList<>();
		yamlConfig.fields().forEachRemaining(field -> fieldNames.add(field.getKey()));

		// Sort the configuration names by the import order
		fieldNames.sort((name1, name2) -> {
			final var order1 = CONFIGURATION_IMPORT_ORDER.indexOf(name1);
			final var order2 = CONFIGURATION_IMPORT_ORDER.indexOf(name2);
			return Integer.compare(order1, order2);
		});

		// Import the configurations
		for (final String configName : fieldNames) {
			final var configEntry = yamlConfig.get(configName);

			seenConfigs.add(configName);

			if (parameters.getConfigurationsToImport() != null &&
			    !parameters.getConfigurationsToImport().contains(configName)) {
				importSummary.addIgnored(configName);
				continue;
			}

			switch (configName) {
				case ConfigurationFile.PROJECT_CONFIGURATION_KEY ->
						importProjectConfiguration(project, configEntry, importSummary);
				case ConfigurationFile.DATA_SOURCE_CONFIGURATION_KEY ->
						importDataSourceConfiguration(project, configEntry, importSummary);
				case ConfigurationFile.FILE_CONFIGURATION_KEY ->
						importFileConfiguration(project, configEntry, importSummary);
				case ConfigurationFile.DATA_CONFIGURATION_KEY ->
						importDataConfiguration(project, configEntry, importSummary);
				case ConfigurationFile.DATASET_CONFIGURATION_KEY ->
						importDatasetConfiguration(project, configEntry, importSummary);
				case ConfigurationFile.PIPELINE_CONFIGURATION_KEY ->
						importPipelinesConfiguration(project, configEntry, importSummary);
				default -> importExternalConfiguration(project, configEntry, configName, importSummary);
			}
		}

		// Check if all configurations to import were in the file
		if (parameters.getConfigurationsToImport() != null) {
			for (final var configToImport : parameters.getConfigurationsToImport()) {
				if (!seenConfigs.contains(configToImport)) {
					var e = new BadConfigurationNameException(BadConfigurationNameException.NO_CONFIGURATION,
					                                          "The file does not contain the configuration " +
					                                          configToImport);
					importSummary.addError(configToImport, e.getErrorCode(), e.getMessage());
				}
			}
		}

		// Check if the import failed according to the partial import
		if (importSummary.getStatus() == ConfigurationImportSummary.ConfigurationImportStatus.ERROR) {
			// Throw exception to trigger rollback
			throw new BadConfigurationFileException(BadConfigurationFileException.IMPORT_FAILED,
			                                        "Failed to import configurations!",
			                                        new ErrorDetails().withConfigurationImportSummary(importSummary));
		}

		return importSummary;
	}


	/**
	 * Loads the configuration with the given name from the database.
	 * Also supports keys defined in {@link ConfigurationFile}.
	 * The returned type is one of the following:
	 * <ul>
	 *     <li>{@link ProjectConfigurationDTO}</li>
	 *     <li>{@link DataSourceConfiguration}</li>
	 *     <li>{@link FileConfiguration}</li>
	 *     <li>{@link DataConfiguration}</li>
	 *     <li>{@link DatasetConfiguration}</li>
	 *     <li>{@link PipelinesConfigurationDTO}</li>
	 *     <li>{@link ExternalConfigurationWrapper}</li>
	 * </ul>
	 *
	 * @param configurationName The name of the configuration.
	 * @param project           The project.
	 * @return The configuration as a DataConfiguration object or plain String.
	 * @throws BadConfigurationNameException If the project does not have a configuration with the given name.
	 * @throws BadStateException             If the data configuration does not exist.
	 * @throws InternalIOException           If the DataConfiguration could not be deserialized from the stored JSON.
	 * @throws InternalInvalidStateException If the configuration is not valid.
	 */
	public ConfigurationDTO loadConfiguration(
			final String configurationName,
			final ProjectEntity project
	) throws BadConfigurationNameException, BadStateException, InternalIOException, InternalInvalidStateException {
		switch (configurationName) {
			case ConfigurationFile.PROJECT_CONFIGURATION_KEY -> {
				return projectService.exportProjectConfiguration(project);
			}
			case ConfigurationFile.DATA_SOURCE_CONFIGURATION_KEY -> {
				return databaseService.exportDataSourceConfiguration(project);
			}
			case ConfigurationFile.FILE_CONFIGURATION_KEY -> {
				return databaseService.exportFileConfiguration(project);
			}
			case ConfigurationFile.DATA_CONFIGURATION_KEY -> {
				return databaseService.exportOriginalDataConfiguration(project);
			}
			case ConfigurationFile.DATASET_CONFIGURATION_KEY -> {
				return databaseService.getDatasetConfiguration(project);
			}
			case ConfigurationFile.PIPELINE_CONFIGURATION_KEY -> {
				return processService.exportPipelinesConfiguration(project);
			}
			default -> {
				final var s = databaseService.exportConfiguration(configurationName, project);
				try {
					return yamlMapper.readValue(s, ExternalConfigurationWrapper.class);
				} catch (final JsonProcessingException e) {
					throw new InternalInvalidStateException(InternalInvalidStateException.INVALID_CONFIGURATION,
					                                        "Failed to deserialize configuration from database!",
					                                        e);
				}
			}
		}
	}

	/**
	 * Imports the project configuration from the given JsonNode.
	 * Adds the result to the import summary.
	 *
	 * @param project          The project to import the configuration to.
	 * @param config           The configuration JsonNode.
	 * @param outImportSummary The import summary to update with the result of the import.
	 */
	private void importProjectConfiguration(final ProjectEntity project,
	                                        final JsonNode config,
	                                        final ConfigurationImportSummary outImportSummary) {
		// Convert the tree into a ProjectConfigurationDTO object
		final ProjectConfigurationDTO projectConfiguration;
		try {
			projectConfiguration = yamlMapper.treeToValue(config, ProjectConfigurationDTO.class);
		} catch (final JsonProcessingException e) {
			final ApiException cause = new BadConfigurationFileException(
					BadConfigurationFileException.PROJECT_CONFIGURATION_DESERIALIZATION,
					"Failed to serialize project configuration!", e);
			outImportSummary.addError(ConfigurationFile.PROJECT_CONFIGURATION_KEY, cause.getErrorCode(),
			                          cause.getMessage());
			return;
		}

		// Validate the project configuration
		final Set<ConstraintViolation<ProjectConfigurationDTO>> violations = validator.validate(projectConfiguration);
		if (!violations.isEmpty()) {
			outImportSummary.addError(ConfigurationFile.PROJECT_CONFIGURATION_KEY,
			                          ApiException.assembleErrorCode(ApiException.VALIDATION,
			                                                         ApiExceptionHandler.VALIDATION_ERROR, "1"),
			                          violations);
			return;
		}

		// Update the project configuration
		projectService.updateProjectConfiguration(project, projectConfiguration);
		outImportSummary.addSuccess(ConfigurationFile.PROJECT_CONFIGURATION_KEY);
	}

	/**
	 * Imports the data configuration from the given JsonNode.
	 * Adds the result to the import summary.
	 *
	 * @param project          The project to import the configuration to.
	 * @param config           The configuration JsonNode.
	 * @param outImportSummary The import summary to update with the result of the import.
	 */
	private void importDataConfiguration(final ProjectEntity project,
	                                     final JsonNode config,
	                                     final ConfigurationImportSummary outImportSummary) {
		// 1. Convert the tree into a DataConfiguration object
		final DataConfiguration dataConfiguration;
		try {
			final JsonNode singleConfigNode = yamlMapper.createObjectNode().set(
					ConfigurationFile.DATA_CONFIGURATION_KEY, config);
			dataConfiguration = yamlMapper.treeToValue(singleConfigNode, DataConfiguration.class);
		} catch (final JsonProcessingException e) {
			final ApiException cause = new BadConfigurationFileException(
					BadConfigurationFileException.DATA_CONFIGURATION_DESERIALIZATION,
					"Failed to serialize data configuration!", e);
			outImportSummary.addError(ConfigurationFile.DATA_CONFIGURATION_KEY, cause.getErrorCode(),
			                          cause.getMessage());
			return;
		}

		// 2. No validation to allow manual editing of the attribute configuration

		// 3. Store the DataConfiguration
		try {
			databaseService.storeOriginalDataConfiguration(dataConfiguration, project);
			outImportSummary.addSuccess(ConfigurationFile.DATA_CONFIGURATION_KEY);
		} catch (final ApiException e) {
			outImportSummary.addError(ConfigurationFile.DATA_CONFIGURATION_KEY, e.getErrorCode(), e.getMessage());
		}
	}

	private void importDataSourceConfiguration(final ProjectEntity project,
	                                           final JsonNode config,
	                                           final ConfigurationImportSummary outImportSummary) {
		// 1. Convert the tree into a DataSourceConfiguration object
		final DataSourceConfiguration dataSourceConfiguration;
		try {
			dataSourceConfiguration = yamlMapper.treeToValue(config, DataSourceConfiguration.class);
		} catch (final JsonProcessingException e) {
			final ApiException cause = new BadConfigurationFileException(
					BadConfigurationFileException.DATA_SOURCE_CONFIGURATION_DESERIALIZATION,
					"Failed to deserialize the data source configuration!", e);
			outImportSummary.addError(ConfigurationFile.DATA_SOURCE_CONFIGURATION_KEY, cause.getErrorCode(),
			                          cause.getMessage());
			return;
		}

		// 2. Validate the DataSourceConfiguration
		final Set<ConstraintViolation<DataSourceConfiguration>> violations = validator.validate(dataSourceConfiguration);
		if (!violations.isEmpty()) {
			outImportSummary.addError(ConfigurationFile.DATA_SOURCE_CONFIGURATION_KEY,
			                          ApiException.assembleErrorCode(ApiException.VALIDATION,
			                                                         ApiExceptionHandler.VALIDATION_ERROR, "1"),
			                          violations);
			return;
		}

		// 3. Import the DataSourceConfiguration
		try {
			databaseService.storeDataSourceConfiguration(project, dataSourceConfiguration);
			outImportSummary.addSuccess(ConfigurationFile.DATA_SOURCE_CONFIGURATION_KEY);
		} catch (final ApiException e) {
			outImportSummary.addError(ConfigurationFile.DATA_SOURCE_CONFIGURATION_KEY, e.getErrorCode(),
			                          e.getMessage());
		}

	}

	private void importFileConfiguration(final ProjectEntity project,
	                                     final JsonNode config,
	                                     final ConfigurationImportSummary outImportSummary) {
		// 1. Convert the tree into a FileConfiguration object
		final FileConfiguration fileConfiguration;
		try {
			fileConfiguration = yamlMapper.treeToValue(config, FileConfiguration.class);
		} catch (final JsonProcessingException e) {
			final ApiException cause = new BadConfigurationFileException(
					BadConfigurationFileException.FILE_CONFIGURATION_DESERIALIZATION,
					"Failed to deserialize the file configuration!", e);
			outImportSummary.addError(ConfigurationFile.FILE_CONFIGURATION_KEY, cause.getErrorCode(),
			                          cause.getMessage());
			return;
		}

		// 2. Validate the FileConfiguration
		final Set<ConstraintViolation<FileConfiguration>> violations = validator.validate(fileConfiguration);
		if (!violations.isEmpty()) {
			outImportSummary.addError(ConfigurationFile.FILE_CONFIGURATION_KEY,
			                          ApiException.assembleErrorCode(ApiException.VALIDATION,
			                                                         ApiExceptionHandler.VALIDATION_ERROR, "1"),
			                          violations);
			return;
		}

		// 3. Import the FileConfiguration
		try {
			databaseService.storeFileConfiguration(project, fileConfiguration);
			outImportSummary.addSuccess(ConfigurationFile.FILE_CONFIGURATION_KEY);
		} catch (final ApiException e) {
			outImportSummary.addError(ConfigurationFile.FILE_CONFIGURATION_KEY, e.getErrorCode(), e.getMessage());
		}
	}

	private void importDatasetConfiguration(final ProjectEntity project,
	                                        final JsonNode config,
	                                        final ConfigurationImportSummary outImportSummary) {
		// 1. Convert the tree into a DatasetConfiguration object
		final DatasetConfiguration datasetConfiguration;
		try {
			datasetConfiguration = yamlMapper.treeToValue(config, DatasetConfiguration.class);
		} catch (final JsonProcessingException e) {
			final ApiException cause = new BadConfigurationFileException(
					BadConfigurationFileException.DATASET_CONFIGURATION_DESERIALIZATION,
					"Failed to deserialize the dataset configuration!", e);
			outImportSummary.addError(ConfigurationFile.DATASET_CONFIGURATION_KEY, cause.getErrorCode(),
			                          cause.getMessage());
			return;
		}

		// 2. Validate the DatasetConfiguration
		final Set<ConstraintViolation<DatasetConfiguration>> violations = validator.validate(datasetConfiguration);
		if (!violations.isEmpty()) {
			outImportSummary.addError(ConfigurationFile.DATASET_CONFIGURATION_KEY,
			                          ApiException.assembleErrorCode(ApiException.VALIDATION,
			                                                         ApiExceptionHandler.VALIDATION_ERROR, "1"),
			                          violations);
			return;
		}

		// 3. Import the DatasetConfiguration
		try {
			databaseService.storeDatasetConfiguration(project, datasetConfiguration);
			outImportSummary.addSuccess(ConfigurationFile.DATASET_CONFIGURATION_KEY);
		} catch (final ApiException e) {
			outImportSummary.addError(ConfigurationFile.DATASET_CONFIGURATION_KEY, e.getErrorCode(), e.getMessage());
		}
	}

	/**
	 * Imports the pipelines from the given JsonNode.
	 *
	 * @param project          The project to import the pipelines to.
	 * @param config           The configuration JsonNode.
	 * @param outImportSummary The import summary to update with the result of the import.
	 */
	private void importPipelinesConfiguration(final ProjectEntity project,
	                                          final JsonNode config,
	                                          final ConfigurationImportSummary outImportSummary) {

		// Convert the tree into a ProjectConfigurationDTO object
		final PipelinesConfigurationDTO pipelines;
		try {
			pipelines = yamlMapper.treeToValue(config, PipelinesConfigurationDTO.class);
		} catch (final JsonProcessingException e) {
			final ApiException cause = new BadConfigurationFileException(
					BadConfigurationFileException.PIPELINES_CONFIGURATION_DESERIALIZATION,
					"Failed to deserialize pipelines configuration!", e);
			outImportSummary.addError(ConfigurationFile.PIPELINE_CONFIGURATION_KEY, cause.getErrorCode(),
			                          cause.getMessage());
			return;
		}

		// Validate the project configuration
		final Set<ConstraintViolation<PipelinesConfigurationDTO>> violations = validator.validate(pipelines);
		if (!violations.isEmpty()) {
			outImportSummary.addError(ConfigurationFile.PIPELINE_CONFIGURATION_KEY,
			                          ApiException.assembleErrorCode(ApiException.VALIDATION,
			                                                         ApiExceptionHandler.VALIDATION_ERROR, "1"),
			                          violations);
			return;
		}

		try {
			processService.configurePipelines(project, pipelines);
			outImportSummary.addSuccess(ConfigurationFile.PIPELINE_CONFIGURATION_KEY);
		} catch (final BadStepNameException | BadStateException | InternalInvalidStateException e) {
			outImportSummary.addError(ConfigurationFile.PIPELINE_CONFIGURATION_KEY, e.getErrorCode(), e.getMessage());
		}
	}

	/**
	 * Imports a single configuration for an external module in the form of a JsonNode.
	 * Adds the result to the import summary.
	 * If the import fails, instead of throwing an exception, the error code is added to the import summary.
	 *
	 * @param project          The project the configuration is imported to.
	 * @param config           The configuration JsonNode.
	 * @param configName       The name of the configuration.
	 * @param outImportSummary The import summary to update with the result of the import.
	 */
	private void importExternalConfiguration(final ProjectEntity project,
	                                         final JsonNode config,
	                                         final String configName,
	                                         final ConfigurationImportSummary outImportSummary) {
		try {
			final ConfigurationPart part;
			try {
				part = yamlMapper.treeToValue(config, ConfigurationPart.class);
			} catch (final JsonProcessingException e) {
				throw new BadConfigurationFileException(BadConfigurationFileException.CONFIGURATION_DESERIALIZATION,
				                                        "Failed to parse the configuration tree!", e);
			}

			importExternalConfiguration(project, part, configName);
			outImportSummary.addSuccess(configName);
		} catch (final ApiException e) {
			outImportSummary.addError(configName, e.getErrorCode(), e.getMessage());
		}
	}

	/**
	 * Imports a single configuration for an external module in the form of a configuration part.
	 *
	 * @param project    The project the configuration is imported to.
	 * @param part       The configuration part.
	 * @param configName The name of the configuration.
	 * @throws BadAlgorithmException         If no algorithm is specified in the configuration.
	 * @throws BadConfigurationNameException If the configuration name is not valid.
	 * @throws BadStateException             If the process is running or scheduled.
	 * @throws InternalIOException           If serializing the configuration part failed.
	 */
	private void importExternalConfiguration(final ProjectEntity project,
	                                         final ConfigurationPart part,
	                                         final String configName
	) throws BadAlgorithmException, BadConfigurationNameException, BadStateException, InternalIOException {
		// Configuration is for an external module
		stepService.getExternalConfiguration(configName);

		// Validate the syntax of the configuration
		if (!validateAlgorithm(configName, part)) {
			throw new BadAlgorithmException(BadAlgorithmException.ALGORITHM_NOT_SELECTED, "No algorithm specified!");
		}

		// Store the configuration
		try {
			final var tree = yamlMapper.valueToTree(part);
			final JsonNode singleConfigNode = yamlMapper.createObjectNode().set(configName, tree);
			databaseService.storeConfiguration(configName, yamlMapper.writeValueAsString(singleConfigNode), project);
		} catch (final JsonProcessingException e) {
			throw new InternalIOException(InternalIOException.CONFIGURATION_SERIALIZATION,
			                              "Failed to serialize configuration!", e);
		}
	}

	/**
	 * Validates if the configuration part contains a valid algorithm definition.
	 * If the algorithm is defined but not as a standardized algorithm definition,
	 * the algorithm definition is being set.
	 *
	 * @param configName The name of the configuration.
	 * @param part       The configuration part.
	 * @return True if the algorithm definition is valid, false otherwise.
	 */
	private boolean validateAlgorithm(final String configName, final ConfigurationPart part) {
		if (part.getAlgorithm() != null) {
			// New standardized algorithm definition
			if (part.getAlgorithm().getId() != null && part.getAlgorithm().getVersion() != null) {
				return true;
			}

			if (part.getAlgorithm().getConfiguration().containsKey("synthesizer") &&
			    part.getAlgorithm().getVersion() != null) {
				// Old algorithm definitions of the synthetization module for backwards compatibility
				part.getAlgorithm().setId(part.getAlgorithm().getConfiguration().get("synthesizer").asText());
				return true;
			}

			return false;

		} else {
			// Old algorithm definitions of the modules for backwards compatibility
			final AlgorithmSelector selector = new AlgorithmSelector();
			part.setAlgorithm(selector);
			switch (configName) {
				case "anonymization" -> {
					if (part.getConfiguration().containsKey("privacyModels")) {
						selector.setId(part.getConfiguration().get("privacyModels").path(0).path("name").asText());
					}
					selector.setVersion(("1.0.0"));
				}
				case "risk_assessment_configuration" -> {
					selector.setId("risk_assessment");
					selector.setVersion(("0.1.0"));
				}
				case "evaluation_configuration" -> {
					selector.setId("evaluation");
					selector.setVersion(("0.1.0"));
				}
			}

			return selector.getId() != null && !selector.getId().isBlank();
		}
	}

}
