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
import de.kiaim.cinnamon.platform.model.configuration.*;
import de.kiaim.cinnamon.platform.model.dto.*;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.io.IOException;
import java.util.Collection;

/**
 * Service for forwarding request regarding configurations to the external server.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class ExternalConfigurationService {

	/**
	 * Key for the data configuration (see {@link de.kiaim.cinnamon.model.configuration.data.DataConfiguration}).
	 * Matches the name of the field {@link de.kiaim.cinnamon.model.configuration.data.DataConfiguration#getConfigurations()}.
	 */
	private static final String DATA_CONFIGURATION_KEY = "configurations";

	private final ObjectMapper yamlMapper;

	private final DatabaseService databaseService;
	private final ExternalServerInstanceService externalServerInstanceService;
	private final HttpService httpService;
	private final StepService stepService;

	public ExternalConfigurationService(final SerializationConfig serializationConfig,
	                                    final DatabaseService databaseService,
	                                    final ExternalServerInstanceService externalServerInstanceService,
	                                    final HttpService httpService, final StepService stepService) {
		this.yamlMapper = serializationConfig.yamlMapper();
		this.databaseService = databaseService;
		this.externalServerInstanceService = externalServerInstanceService;
		this.httpService = httpService;
		this.stepService = stepService;
	}

	/**
	 * Creates information about the given configuration.
	 *
	 * @param configurationName The name of the configuration.
	 * @param project           The project.
	 * @return The configuration page information.
	 * @throws BadConfigurationNameException If no configuration with the given name exists.
	 * @throws InternalInvalidStateException If the internal state is invalid.
	 */
	public ConfigurationInfo getInfo(final String configurationName, final ProjectEntity project)
			throws BadConfigurationNameException, InternalInvalidStateException {
		final ConfigurationInfo configurationInfo = new ConfigurationInfo();

		final ExternalConfiguration externalConfiguration = stepService.getExternalConfiguration(configurationName);

		final var jobs = externalConfiguration.getUsages()
		                                      .stream()
		                                      .map(ExternalEndpoint::getUsages)
		                                      .flatMap(Collection::stream)
		                                      .toList();

		for (final Job job : jobs) {
			if (job.getStage() == null) {
				// Job is not used so skip it
				continue;
			}

			final var process = project.getPipelines().get(0).getStageByJob(job);
			if (process.isEmpty()) {
				throw new InternalInvalidStateException(InternalInvalidStateException.MISSING_PROCESS_ENTITY,
				                                        "No process entity for job '" + job.getName() + "' available!");
			}

			final boolean requiresHoldOut = stepService.requiresHoldOutSplit(job);
			final boolean holdOutFulfilled = !requiresHoldOut || project.getOriginalData().isHasHoldOut();

			final boolean skip = process.get().isSkip() || !holdOutFulfilled;
			final boolean isConfigured = process.get().getConfiguration() != null;

			final var processInfo = new ProcessInfo(job.getName(), skip, holdOutFulfilled, isConfigured);
			configurationInfo.getProcesses().add(processInfo);
		}

		return configurationInfo;
	}

	/**
	 * Fetches the available algorithms from the external server associated with the configuration.
	 *
	 * @param configurationName The configuration name associated with the external configuration.
	 * @return A YAML string with the available algorithms.
	 * @throws BadConfigurationNameException If the configuration name is not valid.
	 * @throws InternalRequestException      If the request fetching the algorithms failed.
	 */
	public String fetchAvailableAlgorithms(final String configurationName)
			throws BadConfigurationNameException, InternalRequestException {
		final ExternalConfiguration externalConfiguration = stepService.getExternalConfiguration(configurationName);
		final ExternalServer externalServer = externalConfiguration.getExternalServer();
		final ExternalServerInstance instance = externalServerInstanceService.findAvailableExternalServerInstance(
				externalServer, true);

		if (instance == null) {
			throw new InternalRequestException(InternalRequestException.NO_INSTANCE_AVAILABLE,
			                                   "No available external server instance found for configuration '" +
			                                   configurationName + "'");
		}

		final String serverUrl = instance.getUrl();
		final String urlPath = externalConfiguration.getAlgorithmEndpoint();

		try {
			final WebClient webClient = WebClient.builder().baseUrl(serverUrl).build();
			return webClient.get()
			                .uri(urlPath)
			                .retrieve()
			                .onStatus(HttpStatusCode::isError,
			                          errorResponse -> errorResponse.toEntity(String.class)
			                                                        .map(httpService::buildErrorResponse))
			                .bodyToMono(String.class)
			                .block();
		} catch (final RequestRuntimeException e) {
			final String message = httpService.buildError(e, "fetch the status");
			final ErrorDetails errorDetails = new ErrorDetails().withConfigurationName(configurationName);
			throw new InternalRequestException(InternalRequestException.ALGORITHMS, message, errorDetails);
		} catch (WebClientRequestException e) {
			var message = "Failed to fetch available algorithms for configuration '" + configurationName + "'! " +
			              e.getMessage();
			final ErrorDetails errorDetails = new ErrorDetails().withConfigurationName(configurationName);
			throw new InternalRequestException(InternalRequestException.ALGORITHMS, message, errorDetails);
		}
	}

	/**
	 * Fetches the configuration definition from the external server for the given algorithm and injects parameters.
	 *
	 * @param project           The project for resolving the parameter values.
	 * @param configurationName Name of the configuration associated with the external configuration.
	 * @param definitionPath    The path under which the configuration definition is available.
	 * @return A YAML string containing the configuration definition.
	 * @throws BadConfigurationNameException       If the configuration name is not valid.
	 * @throws InternalDataSetPersistenceException If retrieving parameters from the database failed.
	 * @throws InternalInvalidStateException       If getting the dataset info failed.
	 * @throws InternalRequestException            If the request fetching the definition failed.
	 */
	public String fetchAlgorithmDefinition(final ProjectEntity project, final String configurationName,
	                                       final String definitionPath)
			throws BadConfigurationNameException, InternalDataSetPersistenceException, InternalInvalidStateException, InternalRequestException {
		final String configuration = fetchAlgorithmDefinition(configurationName, definitionPath);
		return injectParameters(project, configuration);
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
					databaseService.storeConfiguration(configName, null,
					                                   yamlMapper.writeValueAsString(singleConfigNode), project);
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
	 * Fetches the configuration definition from the external server for the given algorithm.
	 *
	 * @param configurationName Name of the configuration associated with the external configuration.
	 * @param definitionPath    The path under which the configuration definition is available.
	 * @return A YAML string containing the configuration definition.
	 * @throws BadConfigurationNameException If the configuration name is not valid.
	 * @throws InternalRequestException      If the request fetching the definition failed.
	 */
	private String fetchAlgorithmDefinition(final String configurationName,
	                                        final String definitionPath) throws BadConfigurationNameException, InternalRequestException {
		final ExternalConfiguration externalConfiguration = stepService.getExternalConfiguration(configurationName);
		final ExternalServer externalServer = externalConfiguration.getExternalServer();
		final ExternalServerInstance instance = externalServerInstanceService.findAvailableExternalServerInstance(
				externalServer, true);

		if (instance == null) {
			throw new InternalRequestException(InternalRequestException.NO_INSTANCE_AVAILABLE,
			                                   "No available external server instance found for configuration '" +
			                                   configurationName + "'");
		}

		final String serverUrl = instance.getUrl();

		try {
			final WebClient webClient = WebClient.builder().baseUrl(serverUrl).build();
			return webClient.get()
			                .uri(definitionPath)
			                .retrieve()
			                .onStatus(HttpStatusCode::isError,
			                          errorResponse -> errorResponse.toEntity(String.class)
			                                                        .map(httpService::buildErrorResponse))
			                .bodyToMono(String.class)
			                .block();
		} catch (final RequestRuntimeException e) {
			final String message = httpService.buildError(e, "fetch the status");
			final ErrorDetails errorDetails = new ErrorDetails().withConfigurationName(configurationName);
			throw new InternalRequestException(InternalRequestException.CONFIGURATION_DEFINITION, message,
			                                   errorDetails);
		} catch (WebClientRequestException e) {
			var message = "Failed to fetch algorithms definition for configuration '" + configurationName +
			              "' and algorithm '" + definitionPath + "'! " + e.getMessage();

			final ErrorDetails errorDetails = new ErrorDetails().withConfigurationName(configurationName);
			throw new InternalRequestException(InternalRequestException.CONFIGURATION_DEFINITION, message,
			                                   errorDetails);
		}
	}

	/**
	 * Inject parameters into the config string.
	 *
	 * @param project       The project for resolving the parameter values.
	 * @param configuration The configuration string.
	 * @return The configuration string with containing the injected parameter.
	 * @throws InternalInvalidStateException       If getting the dataset info failed.
	 * @throws InternalDataSetPersistenceException If retrieving parameters from the database failed.
	 */
	private String injectParameters(final ProjectEntity project, final String configuration)
			throws InternalDataSetPersistenceException, InternalInvalidStateException {
		String result = configuration;

		// Inject parameters for the original data set
		if (project.getOriginalData().getDataSet() != null) {
			if (configuration.contains("$dataset.original.numberHoldOutRows")) {
				final DataSetInfo info;
				try {
					info = databaseService.getInfo(project.getOriginalData().getDataSet());
				} catch (BadStateException e) {
					throw new InternalInvalidStateException(InternalInvalidStateException.MISSING_DATA_STET, "Failed to get dataset info!", e);
				}
				result = result.replace("$dataset.original.numberHoldOutRows",
				                        String.valueOf(info.getNumberHoldOutRows()));
			}
		}

		return result;
	}

}
