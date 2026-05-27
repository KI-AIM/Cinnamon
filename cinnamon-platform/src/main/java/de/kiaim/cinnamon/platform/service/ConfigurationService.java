package de.kiaim.cinnamon.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.kiaim.cinnamon.model.configuration.ConfigurationPart;
import de.kiaim.cinnamon.model.configuration.algorithms.AlgorithmSelector;
import de.kiaim.cinnamon.model.configuration.data.DataConfiguration;
import de.kiaim.cinnamon.model.dto.ConfigurationImportParameters;
import de.kiaim.cinnamon.model.dto.ConfigurationImportSummary;
import de.kiaim.cinnamon.model.dto.ErrorDetails;
import de.kiaim.cinnamon.platform.config.SerializationConfig;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashSet;

/**
 * Service class for accessing and managing configurations.
 * Implements unified handling for external configurations, the data configuration, and possible future special configurations.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class ConfigurationService {

	/**
	 * Key for the data configuration (see {@link de.kiaim.cinnamon.model.configuration.data.DataConfiguration}).
	 * Matches the name of the field {@link de.kiaim.cinnamon.model.configuration.data.DataConfiguration#getConfigurations()}.
	 */
	public static final String DATA_CONFIGURATION_KEY = "configurations";

	private final ObjectMapper yamlMapper;

	private final DatabaseService databaseService;
	private final StepService stepService;

	public ConfigurationService(
			final SerializationConfig serializationConfig,
			final DatabaseService databaseService,
			final StepService stepService
	) {
		this.yamlMapper = serializationConfig.yamlMapper();
		this.databaseService = databaseService;
		this.stepService = stepService;
	}

	/**
	 * Imports a configuration file into the project.
	 * The root object of the YAML must be an object with its keys being the configuration names as defined in the cinnamon configuration.
	 * Invalid configuration names that are not selected for import will not cause errors.
	 * Configurations of external modules for older versions are updated to be compatible with the current version.
	 * The content of the configurations is not validated.
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

			if (configName.equals(DATA_CONFIGURATION_KEY)) {

				// Convert the tree into a DataConfiguration object
				final DataConfiguration dataConfiguration;
				try {
					final JsonNode singleConfigNode = yamlMapper.createObjectNode().set(configName, configEntry.getValue());
					dataConfiguration = yamlMapper.treeToValue(singleConfigNode, DataConfiguration.class);
				} catch (final JsonProcessingException e) {
					importSummary.addError(configName,
					                       new InternalIOException(InternalIOException.DATA_CONFIGURATION_SERIALIZATION,
					                                               "Failed to serialize data configuration!",
					                                               e).getErrorCode());
					continue;
				}

				// Store the DataConfiguration
				try {
					databaseService.storeOriginalDataConfiguration(dataConfiguration, project);
					importSummary.addSuccess(configName);
				} catch (final BadDataConfigurationException | BadDataSetIdException |
				               InternalDataSetPersistenceException | InternalIOException | BadStateException e) {
					importSummary.addError(configName, e.getErrorCode());
				}

			} else {

				// Configuration is for an external module
				try {
					stepService.getExternalConfiguration(configName);
				} catch (final BadConfigurationNameException e) {
					importSummary.addError(configName, e.getErrorCode());
					continue;
				}

				// Validate the syntax of the configuration
				final ConfigurationPart part;
				try {
					part = yamlMapper.treeToValue(configEntry.getValue(), ConfigurationPart.class);
				} catch (final JsonProcessingException e) {
					importSummary.addError(configName,
					                       new InternalIOException(InternalIOException.CONFIGURATION_DESERIALIZATION,
					                                               null,
					                                               e).getErrorCode());
					continue;
				}

				if (!validateAlgorithm(configName, part)) {
					importSummary.addError(configName,
					                       new BadAlgorithmException(BadAlgorithmException.ALGORITHM_NOT_SELECTED,
					                                                 null).getErrorCode());
					continue;
				}

				// Store the configuration
				try {
					final var tree = yamlMapper.valueToTree(part);
					final JsonNode singleConfigNode = yamlMapper.createObjectNode().set(configName, tree);
					databaseService.storeConfiguration(configName, yamlMapper.writeValueAsString(singleConfigNode),
					                                   project);
					importSummary.addSuccess(configName);
				} catch (final BadStateException | BadConfigurationNameException e) {
					importSummary.addError(configName, e.getErrorCode());
				} catch (final JsonProcessingException e) {
					importSummary.addError(configName,
					                       new InternalIOException(InternalIOException.CONFIGURATION_SERIALIZATION,
					                                               "Failed to serialize configuration!",
					                                               e).getErrorCode());
				}
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
	 * Also supports the data configuration key {@link #DATA_CONFIGURATION_KEY}.
	 *
	 * @param configurationName The name of the configuration.
	 * @param project           The project.
	 * @return The configuration as a DataConfiguration object or plain String.
	 * @throws BadConfigurationNameException If the project does not have a configuration with the given name.
	 * @throws BadStateException             If the data configuration does not exist.
	 * @throws InternalIOException           If the DataConfiguration could not be deserialized from the stored JSON.
	 */
	public Object loadConfiguration(
			final String configurationName,
			final ProjectEntity project
	) throws BadConfigurationNameException, BadStateException, InternalIOException {
		if (DATA_CONFIGURATION_KEY.equals(configurationName)) {
			return databaseService.exportOriginalDataConfiguration(project);
		} else {
			return databaseService.exportConfiguration(configurationName, project);
		}
	}

	/**
	 * Validates if the configuration part contains a valid algorithm definition.
	 * If the algorithm is defined but not as a standardized algorithm definition,
	 * the algorithm definition is being set.
	 *
	 * @param configName The name of the configuration.
	 * @param part The configuration part.
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
					selector.setVersion(("1.0.0"));
				}
				case "evaluation_configuration" -> {
					selector.setId("evaluation");
					selector.setVersion(("1.0.0"));
				}
			}

			return selector.getId() != null && !selector.getId().isBlank();
		}
	}

}
