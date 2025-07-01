package de.kiaim.cinnamon.platform.service;

import de.kiaim.cinnamon.model.dto.ErrorDetails;
import de.kiaim.cinnamon.platform.exception.*;
import de.kiaim.cinnamon.platform.model.configuration.ExternalConfiguration;
import de.kiaim.cinnamon.platform.model.configuration.ExternalEndpoint;
import de.kiaim.cinnamon.platform.model.configuration.ExternalServer;
import de.kiaim.cinnamon.platform.model.configuration.Job;
import de.kiaim.cinnamon.platform.model.dto.ConfigurationInfo;
import de.kiaim.cinnamon.platform.model.dto.DataSetInfo;
import de.kiaim.cinnamon.platform.model.dto.ProcessInfo;
import de.kiaim.cinnamon.platform.model.entity.ProjectEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.util.Collection;

/**
 * Service for forwarding request regarding configurations to the external server.
 *
 * @author Daniel Preciado-Marquez
 */
@Service
public class ExternalConfigurationService {

	private final DatabaseService databaseService;
	private final HttpService httpService;
	private final StepService stepService;

	public ExternalConfigurationService(final DatabaseService databaseService, final HttpService httpService,
	                                    final StepService stepService) {
		this.databaseService = databaseService;
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

			final var processInfo = new ProcessInfo(job.getName(), skip, holdOutFulfilled);
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

		final String serverUrl = externalServer.getUrlServer();
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
	 * @throws InternalRequestException            If the request fetching the definition failed.
	 */
	public String fetchAlgorithmDefinition(final ProjectEntity project, final String configurationName,
	                                       final String definitionPath)
			throws BadConfigurationNameException, InternalDataSetPersistenceException, InternalRequestException {
		final String configuration = fetchAlgorithmDefinition(configurationName, definitionPath);
		return injectParameters(project, configuration);
	}

	/**
	 * Fetches the configuration definition from the external server for the given algorithm.
	 *
	 * @param configurationName Name of the configuration associated with the external configuration.
	 * @param definitionPath The path under which the configuration definition is available.
	 * @return A YAML string containing the configuration definition.
	 * @throws BadConfigurationNameException If the configuration name is not valid.
	 * @throws InternalRequestException      If the request fetching the definition failed.
	 */
	private String fetchAlgorithmDefinition(final String configurationName, final String definitionPath) throws BadConfigurationNameException, InternalRequestException {
		final ExternalConfiguration externalConfiguration = stepService.getExternalConfiguration(configurationName);
		final ExternalServer externalServer = externalConfiguration.getExternalServer();

		final String serverUrl = externalServer.getUrlServer();

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
			throw new InternalRequestException(InternalRequestException.CONFIGURATION_DEFINITION, message, errorDetails);
		} catch (WebClientRequestException e) {
			var message = "Failed to fetch algorithms definition for configuration '" + configurationName +
			              "' and algorithm '" + definitionPath + "'! " + e.getMessage();

			final ErrorDetails errorDetails = new ErrorDetails().withConfigurationName(configurationName);
			throw new InternalRequestException(InternalRequestException.CONFIGURATION_DEFINITION, message, errorDetails);
		}
	}

	/**
	 * Inject parameters into the config string.
	 *
	 * @param project       The project for resolving the parameter values.
	 * @param configuration The configuration string.
	 * @return The configuration string with containing the injected parameter.
	 * @throws InternalDataSetPersistenceException If retrieving parameters from the database failed.
	 */
	private String injectParameters(final ProjectEntity project, final String configuration)
			throws InternalDataSetPersistenceException {
		String result = configuration;

		// Inject parameters for the original data set
		if (project.getOriginalData().getDataSet() != null) {
			if (configuration.contains("$dataset.original.numberHoldOutRows")) {
				final DataSetInfo info = databaseService.getInfo(project.getOriginalData().getDataSet());
				result = result.replace("$dataset.original.numberHoldOutRows",
				                        String.valueOf(info.getNumberHoldOutRows()));
			}
		}

		return result;
	}

}
