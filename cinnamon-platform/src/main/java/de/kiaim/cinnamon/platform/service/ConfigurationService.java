package de.kiaim.cinnamon.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.configuration.ConfigurationFile;
import de.kiaim.cinnamon.model.configuration.ConfigurationPart;
import de.kiaim.cinnamon.model.configuration.algorithms.AlgorithmSelector;
import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.configuration.project.ProjectConfigurationDTO;
import de.kiaim.cinnamon.model.dto.ConfigurationImportParameters;
import de.kiaim.cinnamon.model.dto.ConfigurationImportSummary;
import de.kiaim.cinnamon.model.dto.ErrorDetails;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import de.kiaim.cinnamon.platform.controller.ApiExceptionHandler;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Service class for accessing and managing configurations.
 * Implements unified handling for external configurations, the data configuration, and possible future special configurations.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class ConfigurationService {

	private final ObjectMapper yamlMapper;

	private final Validator validator;

	private final DatabaseService databaseService;
	private final ProjectService projectService;
	private final StepService stepService;

	public ConfigurationService(
			final SerializationConfig serializationConfig,
			final Validator validator,
			final DatabaseService databaseService,
			final ProjectService projectService,
			final StepService stepService
	) {
		this.yamlMapper = serializationConfig.yamlMapper();
		this.validator = validator;
		this.databaseService = databaseService;
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
	 * @throws BadFileException              If the file cannot be read.
	 */
	@Transactional(rollbackFor = {BadConfigurationFileException.class})
	public ConfigurationImportSummary importConfigurations(
			final ProjectEntity project,
			final MultipartFile file,
			final ConfigurationImportParameters parameters
	) throws BadConfigurationFileException, BadFileException {
		final JsonNode yamlConfig;

		try {
			yamlConfig = yamlMapper.readTree(file.getInputStream());
		} catch (JsonProcessingException e) {
			throw new BadConfigurationFileException(BadConfigurationFileException.INVALID_YAML,
			                                        "Invalid YAML file format", e);
		} catch (IOException e) {
			throw new BadFileException(BadFileException.NOT_READABLE, "File could not be read", e);
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

		// Iterate over all configurations in the file and import those marked for import
		final var configItr = yamlConfig.fields();

		while (configItr.hasNext()) {
			final var configEntry = configItr.next();
			final var configName = configEntry.getKey();

			seenConfigs.add(configName);

			if (parameters.getConfigurationsToImport() != null &&
			    !parameters.getConfigurationsToImport().contains(configName)) {
				importSummary.addIgnored(configName);
				continue;
			}

			if (configName.equals(ConfigurationFile.PROJECT_CONFIGURATION_KEY)) {
				importProjectConfiguration(project, configEntry.getValue(), importSummary);
			} else if (configName.equals(ConfigurationFile.DATA_CONFIGURATION_KEY)) {
				importDataConfiguration(project, configEntry.getValue(), importSummary);
			} else {
				importExternalConfiguration(project, configEntry.getValue(), configName, importSummary);
			}
		}

		// Check if all configurations to import were in the file
		if (parameters.getConfigurationsToImport() != null) {
			for (final var configToImport : parameters.getConfigurationsToImport()) {
				if (!seenConfigs.contains(configToImport)) {
					importSummary.addError(configToImport, new BadConfigurationNameException(
							BadConfigurationNameException.NO_CONFIGURATION,
							"The file does not contain the configuration " + configToImport).getErrorCode());
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
	 *     <li>{@link DataConfiguration}</li>
	 *     <li>{@code Map.Entry<String, ConfigurationPart>}</li>
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
	public Object loadConfiguration(
			final String configurationName,
			final ProjectEntity project
	) throws BadConfigurationNameException, BadStateException, InternalIOException, InternalInvalidStateException {
		if (ConfigurationFile.PROJECT_CONFIGURATION_KEY.equals(configurationName)) {
			return projectService.exportProjectConfiguration(project);
		} else if (ConfigurationFile.DATA_CONFIGURATION_KEY.equals(configurationName)) {
			return databaseService.exportOriginalDataConfiguration(project);
		} else {
			final var s = databaseService.exportConfiguration(configurationName, project);
			try {
				final var a = yamlMapper.readValue(s, ConfigurationFile.class);
				return a.getParts().entrySet().stream().filter(e -> e.getKey().equals(configurationName)).findFirst()
				        .orElseThrow(() -> new InternalInvalidStateException(
						        InternalInvalidStateException.INVALID_CONFIGURATION,
						        "Configuration key not found: " + configurationName));
			} catch (final JsonProcessingException e) {
				throw new InternalInvalidStateException(InternalInvalidStateException.INVALID_CONFIGURATION,
				                                        "Failed to deserialize configuration from database!",
				                                        e);
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
			outImportSummary.addError(ConfigurationFile.PROJECT_CONFIGURATION_KEY,
			                          new InternalIOException(InternalIOException.PROJECT_CONFIGURATION_DESERIALIZATION,
			                                                  "Failed to serialize project configuration!",
			                                                  e).getErrorCode());
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
		// Convert the tree into a DataConfiguration object
		final DataConfiguration dataConfiguration;
		try {
			final JsonNode singleConfigNode = yamlMapper.createObjectNode()
			                                            .set(ConfigurationFile.DATA_CONFIGURATION_KEY, config);
			dataConfiguration = yamlMapper.treeToValue(singleConfigNode, DataConfiguration.class);
		} catch (final JsonProcessingException e) {
			outImportSummary.addError(ConfigurationFile.DATA_CONFIGURATION_KEY,
			                          new InternalIOException(InternalIOException.DATA_CONFIGURATION_SERIALIZATION,
			                                                  "Failed to serialize data configuration!",
			                                                  e).getErrorCode());
			return;
		}

		// Store the DataConfiguration
		try {
			databaseService.storeOriginalDataConfiguration(dataConfiguration, project);
			outImportSummary.addSuccess(ConfigurationFile.DATA_CONFIGURATION_KEY);
		} catch (final BadDataConfigurationException | BadDataSetIdException |
		               InternalDataSetPersistenceException | InternalIOException | BadStateException e) {
			outImportSummary.addError(ConfigurationFile.DATA_CONFIGURATION_KEY, e.getErrorCode());
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
				throw new InternalIOException(InternalIOException.CONFIGURATION_DESERIALIZATION,
				                              "Failed to parse the configuration tree!", e);
			}

			importExternalConfiguration(project, part, configName);
			outImportSummary.addSuccess(configName);
		} catch (final ApiException e) {
			outImportSummary.addError(configName, e.getErrorCode());
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
					selector.setVersion(("0.1.0"));
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
