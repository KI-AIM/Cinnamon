package de.kiaim.cinnamon.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

		final var configItr = yamlConfig.fields();

		while (configItr.hasNext()) {
			final var configEntry = configItr.next();
			final var configName = configEntry.getKey();

			if (parameters.getConfigurationsToImport() != null &&
			    !parameters.getConfigurationsToImport().contains(configName)) {
				importSummary.addIgnored(configName);
				continue;
			}

			final JsonNode singleConfigNode = yamlMapper.createObjectNode().set(configName, configEntry.getValue());

			if (configName.equals(DATA_CONFIGURATION_KEY)) {

				final DataConfiguration dataConfiguration;
				try {
					dataConfiguration = yamlMapper.treeToValue(singleConfigNode, DataConfiguration.class);
				} catch (final JsonProcessingException e) {
					importSummary.addError(configName,
					                       new InternalIOException(InternalIOException.DATA_CONFIGURATION_SERIALIZATION,
					                                               "Failed to serialize data configuration!",
					                                               e).getErrorCode());
					continue;
				}

				try {
					databaseService.storeOriginalDataConfiguration(dataConfiguration, project);
					importSummary.addSuccess(configName);
				} catch (final BadDataConfigurationException | BadDataSetIdException |
				               InternalDataSetPersistenceException | InternalIOException | BadStateException e) {
					importSummary.addError(configName, e.getErrorCode());
				}

			} else {

				try {
					stepService.getExternalConfiguration(configName);
				} catch (final BadConfigurationNameException e) {
					importSummary.addError(configName, e.getErrorCode());
					continue;
				}

				try {
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

}
